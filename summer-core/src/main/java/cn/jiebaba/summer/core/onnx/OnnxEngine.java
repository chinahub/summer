package cn.jiebaba.summer.core.onnx;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ONNX Runtime 推理引擎：基于 JDK 25 的 Foreign Function & Memory API（{@code java.lang.foreign}）
 * 直接调用 onnxruntime 原生共享库，无需 JNI 胶水代码，也无需引入第三方 Java 绑定包。
 * <p>作为共享基础设施，供 summer-ai（文本向量化）与 summer-support（OCR）复用。
 * <p>实现思路：
 * <ol>
 *   <li>通过 {@link SymbolLookup#libraryLookup} 加载 onnxruntime 动态库；</li>
 *   <li>调用导出函数 {@code OrtGetApiBase} 得到 {@code OrtApiBase}，再经其 {@code GetApi(ORT_API_VERSION)}
 *       取得 {@code OrtApi}（一个由函数指针组成的结构体）；</li>
 *   <li>按字段序号（{@code OrtApi} 中函数指针的固定排列）读取各函数指针，包装为 {@link MethodHandle}；</li>
 *   <li>基于 {@link Arena} 管理原生内存：环境/会话长驻共享域，单次推理使用临时受限域。</li>
 * </ol>
 * <p>字段序号与枚举值对应 onnxruntime 1.20.x（{@code ORT_API_VERSION = 20}），底层结构体只追加不重排，
 * 因此对 1.16~1.20 均兼容。支持 FLOAT/FLOAT16/INT64 三种张量元素类型，覆盖 OCR 图像输入与
 * Embedding 模型的 int64 token 输入、FP16 输出场景。
 */
public final class OnnxEngine implements AutoCloseable {

    /** onnxruntime C API 版本号，与所用头文件 {@code ORT_API_VERSION} 对齐。 */
    private static final int ORT_API_VERSION = 20;

    // OrtApi 结构体中所需函数指针的固定序号（onnxruntime 1.20.x）
    private static final int FN_GET_ERROR_CODE = 1;
    private static final int FN_GET_ERROR_MESSAGE = 2;
    private static final int FN_CREATE_ENV = 3;
    private static final int FN_CREATE_SESSION_FROM_ARRAY = 8;
    private static final int FN_RUN = 9;
    private static final int FN_CREATE_SESSION_OPTIONS = 10;
    private static final int FN_SESSION_GET_INPUT_COUNT = 30;
    private static final int FN_SESSION_GET_OUTPUT_COUNT = 31;
    private static final int FN_SESSION_GET_INPUT_NAME = 36;
    private static final int FN_SESSION_GET_OUTPUT_NAME = 37;
    private static final int FN_CREATE_RUN_OPTIONS = 39;
    private static final int FN_CREATE_TENSOR_WITH_DATA = 49;
    private static final int FN_GET_TENSOR_MUTABLE_DATA = 51;
    private static final int FN_GET_TENSOR_ELEMENT_TYPE = 60;
    private static final int FN_GET_DIMENSIONS_COUNT = 61;
    private static final int FN_GET_DIMENSIONS = 62;
    private static final int FN_GET_TENSOR_TYPE_AND_SHAPE = 65;
    private static final int FN_CREATE_CPU_MEMORY_INFO = 69;
    private static final int FN_ALLOCATOR_FREE = 76;
    private static final int FN_GET_ALLOCATOR_WITH_DEFAULT_OPTIONS = 78;
    private static final int FN_RELEASE_ENV = 92;
    private static final int FN_RELEASE_STATUS = 93;
    private static final int FN_RELEASE_MEMORY_INFO = 94;
    private static final int FN_RELEASE_SESSION = 95;
    private static final int FN_RELEASE_VALUE = 96;
    private static final int FN_RELEASE_RUN_OPTIONS = 97;
    private static final int FN_RELEASE_TENSOR_TYPE_AND_SHAPE_INFO = 99;
    private static final int FN_RELEASE_SESSION_OPTIONS = 100;
    private static final int FN_SESSION_GET_MODEL_METADATA = 111;
    private static final int FN_MODEL_METADATA_LOOKUP_CUSTOM = 116;
    private static final int FN_RELEASE_MODEL_METADATA = 118;

    // 枚举常量
    private static final int LOGGING_ERROR = 3;
    private static final int ORT_DEVICE_ALLOCATOR = 0;
    private static final int ORT_MEM_DEFAULT = 0;
    private static final int ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT = 1;
    private static final int ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64 = 7;
    private static final int ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16 = 10;

    private static final AddressLayout ADDRESS = ValueLayout.ADDRESS;
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final ValueLayout.OfLong LONG = ValueLayout.JAVA_LONG;
    private static final ValueLayout.OfFloat FLOAT = ValueLayout.JAVA_FLOAT;
    private static final ValueLayout.OfShort SHORT = ValueLayout.JAVA_SHORT;

    /** 张量元素类型：ONNX 类型码与字节宽度，用于创建输入张量与读取输出。 */
    public enum Type {
        /** 32 位浮点。 */
        FLOAT(ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT, 4),
        /** 16 位浮点（半精度），读取时转为 float。 */
        FLOAT16(ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16, 2),
        /** 64 位整数，用于 token ids 等离散输入。 */
        INT64(ONNX_TENSOR_ELEMENT_DATA_TYPE_INT64, 8);

        private final int onnxType;
        private final int byteSize;

        Type(int onnxType, int byteSize) {
            this.onnxType = onnxType;
            this.byteSize = byteSize;
        }

        public int onnxType() {
            return onnxType;
        }

        public int byteSize() {
            return byteSize;
        }
    }

    /** 输入张量描述：名称、数据、形状与元素类型。data 为 float[]/long[]/short[]，须与 type 匹配。 */
    public record InputTensor(String name, Object data, long[] shape, Type type) {
    }

    /** 推理输出：数据浮点数组与各维度（输出统一转为 float）。 */
    public record Output(float[] data, long[] shape) {
    }

    private final Arena persistent;
    private final MemorySegment api;
    private final MemorySegment env;
    private final MemorySegment memoryInfo;
    private final MemorySegment runOptions;
    private final MemorySegment defaultAllocator;
    private final MethodHandle getErrorCode;
    private final MethodHandle getErrorMessage;
    private final MethodHandle createTensorWithData;
    private final MethodHandle run;
    private final MethodHandle getTensorMutableData;
    private final MethodHandle getTensorTypeAndShape;
    private final MethodHandle getTensorElementType;
    private final MethodHandle getDimensionsCount;
    private final MethodHandle getDimensions;
    private final MethodHandle sessionGetInputCount;
    private final MethodHandle sessionGetOutputCount;
    private final MethodHandle sessionGetInputName;
    private final MethodHandle sessionGetOutputName;
    private final MethodHandle allocatorFree;
    private final MethodHandle getAllocatorWithDefaultOptions;
    private final MethodHandle releaseStatus;
    private final MethodHandle releaseValue;
    private final MethodHandle releaseTensorTypeAndShapeInfo;
    private final MethodHandle createSessionOptions;
    private final MethodHandle createSessionFromArray;
    private final MethodHandle releaseSession;
    private final MethodHandle releaseSessionOptions;
    private final MethodHandle sessionGetModelMetadata;
    private final MethodHandle modelMetadataLookupCustom;
    private final MethodHandle releaseModelMetadata;

    /** 初始化引擎：加载动态库、取得 OrtApi、创建 OrtEnv/MemoryInfo/RunOptions 并包装各函数句柄。 */
    private OnnxEngine(String libPath) {
        this.persistent = Arena.ofShared();
        SymbolLookup lookup = SymbolLookup.libraryLookup(libPath, persistent);
        MemorySegment getApiBaseFn = lookup.find("OrtGetApiBase")
                .orElseThrow(() -> new OnnxException("未在动态库中找到 OrtGetApiBase 符号：" + libPath));
        try {
            var linker = java.lang.foreign.Linker.nativeLinker();
            MethodHandle getApiBase = linker.downcallHandle(getApiBaseFn,
                    FunctionDescriptor.of(ADDRESS));
            MemorySegment apiBase = ((MemorySegment) getApiBase.invoke()).reinterpret(16L);
            // OrtApiBase.GetApi 位于结构体偏移 0
            MemorySegment getApiFn = apiBase.get(ADDRESS, 0L);
            MethodHandle getApi = linker.downcallHandle(getApiFn,
                    FunctionDescriptor.of(ADDRESS, INT));
            this.api = ((MemorySegment) getApi.invoke(ORT_API_VERSION)).reinterpret(4096L);

            this.getErrorCode = apiHandle(linker, FN_GET_ERROR_CODE, FunctionDescriptor.of(INT, ADDRESS));
            this.getErrorMessage = apiHandle(linker, FN_GET_ERROR_MESSAGE, FunctionDescriptor.of(ADDRESS, ADDRESS));
            MethodHandle createEnv = apiHandle(linker, FN_CREATE_ENV, FunctionDescriptor.of(ADDRESS, INT, ADDRESS, ADDRESS));
            MethodHandle createSessionOptions = apiHandle(linker, FN_CREATE_SESSION_OPTIONS, FunctionDescriptor.of(ADDRESS, ADDRESS));
            MethodHandle createSessionFromArray = apiHandle(linker, FN_CREATE_SESSION_FROM_ARRAY,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, LONG, ADDRESS, ADDRESS));
            MethodHandle createCpuMemoryInfo = apiHandle(linker, FN_CREATE_CPU_MEMORY_INFO,
                    FunctionDescriptor.of(ADDRESS, INT, INT, ADDRESS));
            MethodHandle createRunOptions = apiHandle(linker, FN_CREATE_RUN_OPTIONS, FunctionDescriptor.of(ADDRESS, ADDRESS));
            this.run = apiHandle(linker, FN_RUN,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, LONG, ADDRESS, LONG, ADDRESS));
            this.createTensorWithData = apiHandle(linker, FN_CREATE_TENSOR_WITH_DATA,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, LONG, ADDRESS, LONG, INT, ADDRESS));
            this.getTensorMutableData = apiHandle(linker, FN_GET_TENSOR_MUTABLE_DATA, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.getTensorTypeAndShape = apiHandle(linker, FN_GET_TENSOR_TYPE_AND_SHAPE, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.getTensorElementType = apiHandle(linker, FN_GET_TENSOR_ELEMENT_TYPE, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.getDimensionsCount = apiHandle(linker, FN_GET_DIMENSIONS_COUNT, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.getDimensions = apiHandle(linker, FN_GET_DIMENSIONS, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, LONG));
            this.sessionGetInputCount = apiHandle(linker, FN_SESSION_GET_INPUT_COUNT, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.sessionGetOutputCount = apiHandle(linker, FN_SESSION_GET_OUTPUT_COUNT, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.sessionGetInputName = apiHandle(linker, FN_SESSION_GET_INPUT_NAME,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, LONG, ADDRESS, ADDRESS));
            this.sessionGetOutputName = apiHandle(linker, FN_SESSION_GET_OUTPUT_NAME,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, LONG, ADDRESS, ADDRESS));
            this.allocatorFree = apiHandle(linker, FN_ALLOCATOR_FREE, FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.getAllocatorWithDefaultOptions = apiHandle(linker, FN_GET_ALLOCATOR_WITH_DEFAULT_OPTIONS,
                    FunctionDescriptor.of(ADDRESS, ADDRESS));
            this.releaseStatus = apiHandle(linker, FN_RELEASE_STATUS, FunctionDescriptor.ofVoid(ADDRESS));
            this.releaseValue = apiHandle(linker, FN_RELEASE_VALUE, FunctionDescriptor.ofVoid(ADDRESS));
            this.releaseTensorTypeAndShapeInfo = apiHandle(linker, FN_RELEASE_TENSOR_TYPE_AND_SHAPE_INFO,
                    FunctionDescriptor.ofVoid(ADDRESS));

            // 创建全局 OrtEnv
            MemorySegment envOut = persistent.allocate(ADDRESS);
            MemorySegment logId = persistent.allocateFrom("summer-onnx");
            check((MemorySegment) createEnv.invoke(LOGGING_ERROR, logId, envOut));
            this.env = envOut.get(ADDRESS, 0L);

            // 创建默认 CPU MemoryInfo
            MemorySegment memOut = persistent.allocate(ADDRESS);
            check((MemorySegment) createCpuMemoryInfo.invoke(ORT_DEVICE_ALLOCATOR, ORT_MEM_DEFAULT, memOut));
            this.memoryInfo = memOut.get(ADDRESS, 0L);

            // 创建空 RunOptions
            MemorySegment runOptOut = persistent.allocate(ADDRESS);
            check((MemorySegment) createRunOptions.invoke(runOptOut));
            this.runOptions = runOptOut.get(ADDRESS, 0L);

            // 预取默认分配器（用于读取输入/输出名）
            this.defaultAllocator = persistent.allocate(ADDRESS);
            check((MemorySegment) getAllocatorWithDefaultOptions.invoke(defaultAllocator));

            this.createSessionOptions = createSessionOptions;
            this.createSessionFromArray = createSessionFromArray;
            this.releaseSession = apiHandle(linker, FN_RELEASE_SESSION, FunctionDescriptor.ofVoid(ADDRESS));
            this.releaseSessionOptions = apiHandle(linker, FN_RELEASE_SESSION_OPTIONS, FunctionDescriptor.ofVoid(ADDRESS));
            this.sessionGetModelMetadata = apiHandle(linker, FN_SESSION_GET_MODEL_METADATA,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
            this.modelMetadataLookupCustom = apiHandle(linker, FN_MODEL_METADATA_LOOKUP_CUSTOM,
                    FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
            this.releaseModelMetadata = apiHandle(linker, FN_RELEASE_MODEL_METADATA, FunctionDescriptor.ofVoid(ADDRESS));
        } catch (OnnxException e) {
            throw e;
        } catch (Throwable t) {
            throw new OnnxException("初始化 onnxruntime 引擎失败", t);
        }
    }

    /**
     * 加载 onnxruntime 动态库并初始化引擎。
     *
     * @param libPath onnxruntime 共享库路径（Windows 为 onnxruntime.dll，Linux 为 libonnxruntime.so）
     * @return 引擎实例，生命周期内复用同一个 OrtEnv
     */
    public static OnnxEngine load(String libPath) {
        if (!Files.exists(Path.of(libPath))) {
            throw new OnnxException("onnxruntime 动态库不存在：" + libPath);
        }
        return new OnnxEngine(libPath);
    }

    /** 按序号从 OrtApi 结构体读取函数指针并包装为下行调用句柄。 */
    private MethodHandle apiHandle(java.lang.foreign.Linker linker, int index, FunctionDescriptor fd) {
        MemorySegment fp = api.get(ADDRESS, (long) index * ADDRESS.byteSize());
        if (fp.address() == 0L) {
            throw new OnnxException("OrtApi 第 " + index + " 个函数指针为空，onnxruntime 版本可能不兼容");
        }
        return linker.downcallHandle(fp, fd);
    }

    /**
     * 从字节数组加载 ONNX 模型，返回可反复推理的会话。
     *
     * @param modelBytes 模型文件完整字节
     * @return 已加载的模型会话
     */
    public Model loadModel(byte[] modelBytes) {
        try (Arena a = Arena.ofConfined()) {
            MemorySegment optOut = a.allocate(ADDRESS);
            check((MemorySegment) createSessionOptions.invoke(optOut));
            MemorySegment options = optOut.get(ADDRESS, 0L);
            MemorySegment sessOut = a.allocate(ADDRESS);
            MemorySegment data = a.allocate(modelBytes.length);
            data.copyFrom(MemorySegment.ofArray(modelBytes));
            check((MemorySegment) createSessionFromArray.invoke(env, data, (long) modelBytes.length, options, sessOut));
            MemorySegment session = sessOut.get(ADDRESS, 0L);
            releaseSessionOptions.invoke(options);
            String[] inputs = readNames(session, true);
            String[] outputs = readNames(session, false);
            return new Model(session, inputs, outputs);
        } catch (OnnxException e) {
            throw e;
        } catch (Throwable t) {
            throw new OnnxException("加载 ONNX 模型失败", t);
        }
    }

    /**
     * 读取会话的输入或输出名。使用已弃用的 {@code SessionGetInputName/SessionGetOutputName}，
     * 返回的 C 字符串由默认分配器分配，读取后用 {@code AllocatorFree} 释放。
     */
    private String[] readNames(MemorySegment session, boolean input) throws Throwable {
        MethodHandle countFn = input ? sessionGetInputCount : sessionGetOutputCount;
        MethodHandle nameFn = input ? sessionGetInputName : sessionGetOutputName;
        MemorySegment cntOut = persistent.allocate(LONG);
        check((MemorySegment) countFn.invoke(session, cntOut));
        int count = (int) cntOut.get(LONG, 0L);
        String[] names = new String[count];
        MemorySegment allocator = defaultAllocator.get(ADDRESS, 0L);
        for (int i = 0; i < count; i++) {
            MemorySegment nameOut = persistent.allocate(ADDRESS);
            check((MemorySegment) nameFn.invoke(session, (long) i, allocator, nameOut));
            MemorySegment namePtr = nameOut.get(ADDRESS, 0L);
            names[i] = readCString(namePtr);
            check((MemorySegment) allocatorFree.invoke(allocator, namePtr));
        }
        return names;
    }

    /** 读取以 0 结尾的 C 字符串为 Java String。 */
    private String readCString(MemorySegment ptr) {
        if (ptr.address() == 0L) {
            return null;
        }
        // 扫描 0 结尾符确定实际长度后精确读取，兼容超长元数据（如 PP-OCRv6 内嵌字典约 37KB）
        MemorySegment scan = ptr.reinterpret(1 << 20);
        long len = 0;
        while (scan.get(ValueLayout.JAVA_BYTE, len) != 0) {
            len++;
        }
        return scan.reinterpret(len + 1).getString(0L);
    }

    /** 检查 OrtStatus，非空则取出错误信息、释放并抛出异常。 */
    private void check(MemorySegment status) throws Throwable {
        if (status == null || status.address() == 0L) {
            return;
        }
        MemorySegment msgPtr = (MemorySegment) getErrorMessage.invoke(status);
        String msg = msgPtr.address() == 0L ? "未知 onnxruntime 错误" : readCString(msgPtr);
        releaseStatus.invoke(status);
        throw new OnnxException("onnxruntime 错误：" + msg);
    }

    /** IEEE 754 半精度（FP16）转单精度（FP32）：按符号/指数/尾数位重组，处理规格化与非规格化数。 */
    private static float halfToFloat(short h) {
        int bits = h & 0xFFFF;
        int sign = (bits >>> 15) & 0x1;
        int exp = (bits >>> 10) & 0x1F;
        int mant = bits & 0x3FF;
        int f;
        if (exp == 0) {
            if (mant == 0) {
                f = sign << 31;
            } else {
                // 非规格化数：左移尾数直至隐含位为 1，相应扣减指数
                int e = -1;
                do {
                    e++;
                    mant <<= 1;
                } while ((mant & 0x400) == 0);
                mant &= 0x3FF;
                f = (sign << 31) | ((127 - 15 - e) << 23) | (mant << 13);
            }
        } else if (exp == 0x1F) {
            // 无穷或 NaN
            f = (sign << 31) | (0xFF << 23) | (mant << 13);
        } else {
            // 规格化数：指数偏移从 15 调整为 127
            f = (sign << 31) | ((exp - 15 + 127) << 23) | (mant << 13);
        }
        return Float.intBitsToFloat(f);
    }

    /** 已加载的模型会话，线程安全（onnxruntime 的 Run 支持并发调用同一会话）。 */
    public final class Model implements AutoCloseable {
        private final MemorySegment session;
        private final String[] inputNames;
        private final String[] outputNames;
        private volatile boolean closed;

        private Model(MemorySegment session, String[] inputNames, String[] outputNames) {
            this.session = session;
            this.inputNames = inputNames;
            this.outputNames = outputNames;
        }

        public String[] inputNames() {
            return inputNames;
        }

        public String[] outputNames() {
            return outputNames;
        }

        /**
         * 读取模型自定义元数据（如 PP-OCRv4/v6 识别模型内嵌的 character 字典）。
         *
         * @param key 元数据键名
         * @return 对应的值字符串；键不存在时返回 null
         */
        public String getCustomMetadata(String key) {
            try (Arena a = Arena.ofConfined()) {
                MemorySegment metaOut = a.allocate(ADDRESS);
                check((MemorySegment) sessionGetModelMetadata.invoke(session, metaOut));
                MemorySegment metadata = metaOut.get(ADDRESS, 0L);
                try {
                    MemorySegment allocator = defaultAllocator.get(ADDRESS, 0L);
                    MemorySegment keySeg = a.allocateFrom(key);
                    MemorySegment valueOut = a.allocate(ADDRESS);
                    check((MemorySegment) modelMetadataLookupCustom.invoke(metadata, allocator, keySeg, valueOut));
                    MemorySegment valuePtr = valueOut.get(ADDRESS, 0L);
                    if (valuePtr.address() == 0L) {
                        return null;
                    }
                    String value = readCString(valuePtr);
                    check((MemorySegment) allocatorFree.invoke(allocator, valuePtr));
                    return value;
                } finally {
                    releaseModelMetadata.invoke(metadata);
                }
            } catch (OnnxException e) {
                throw e;
            } catch (Throwable t) {
                throw new OnnxException("读取模型元数据失败：" + key, t);
            }
        }

        /**
         * 以单输入（第一个输入名，FLOAT 类型）执行推理，返回第一个输出。
         * 便捷方法，供 OCR 等单浮点输入场景使用；多输入场景用 {@link #run(InputTensor[])}。
         *
         * @param input 输入张量数据（按 C 序 row-major 排布的浮点）
         * @param shape 输入形状，如 {1, 3, 480, 640}
         * @return 第一个输出张量的数据与形状
         */
        public Output run(float[] input, long[] shape) {
            return run(new InputTensor[]{new InputTensor(inputNames[0], input, shape, Type.FLOAT)})[0];
        }

        /**
         * 多输入通用推理：按各输入张量的名称、数据、形状与元素类型创建 OrtValue 并执行 Run，
         * 返回所有输出（统一转为 float，FP16 输出自动转换）。供 Embedding 等多输入场景使用。
         *
         * @param inputs 输入张量数组，data 须为 float[]/long[]/short[] 且与 type 匹配
         * @return 所有输出张量，顺序与模型输出定义一致
         */
        public Output[] run(InputTensor[] inputs) {
            try (Arena a = Arena.ofConfined()) {
                int n = inputs.length;
                MemorySegment inNames = a.allocate(ADDRESS, n);
                MemorySegment inVals = a.allocate(ADDRESS, n);
                int outCount = outputNames.length;
                MemorySegment outNames = a.allocate(ADDRESS, outCount);
                MemorySegment outVals = a.allocate(ADDRESS, outCount);
                try {
                    for (int i = 0; i < n; i++) {
                        InputTensor t = inputs[i];
                        MemorySegment data = allocateData(a, t);
                        MemorySegment shapeSeg = a.allocate(LONG, t.shape().length);
                        shapeSeg.copyFrom(MemorySegment.ofArray(t.shape()));
                        MemorySegment valOut = a.allocate(ADDRESS);
                        check((MemorySegment) createTensorWithData.invoke(memoryInfo, data, data.byteSize(),
                                shapeSeg, (long) t.shape().length, t.type().onnxType(), valOut));
                        MemorySegment val = valOut.get(ADDRESS, 0L);
                        inNames.set(ADDRESS, (long) i * ADDRESS.byteSize(), a.allocateFrom(t.name()));
                        inVals.set(ADDRESS, (long) i * ADDRESS.byteSize(), val);
                    }
                    for (int i = 0; i < outCount; i++) {
                        outNames.set(ADDRESS, (long) i * ADDRESS.byteSize(), a.allocateFrom(outputNames[i]));
                    }
                    check((MemorySegment) OnnxEngine.this.run.invoke(session, runOptions, inNames, inVals, (long) n,
                            outNames, (long) outCount, outVals));
                    Output[] results = new Output[outCount];
                    for (int i = 0; i < outCount; i++) {
                        results[i] = readOutput(outVals.get(ADDRESS, (long) i * ADDRESS.byteSize()), a);
                    }
                    return results;
                } finally {
                    for (int i = 0; i < n; i++) {
                        MemorySegment iv = inVals.get(ADDRESS, (long) i * ADDRESS.byteSize());
                        if (iv.address() != 0L) {
                            releaseValue.invoke(iv);
                        }
                    }
                    for (int i = 0; i < outCount; i++) {
                        MemorySegment ov = outVals.get(ADDRESS, (long) i * ADDRESS.byteSize());
                        if (ov.address() != 0L) {
                            releaseValue.invoke(ov);
                        }
                    }
                }
            } catch (OnnxException e) {
                throw e;
            } catch (Throwable t) {
                throw new OnnxException("ONNX 推理失败", t);
            }
        }

        /** 按输入张量类型将数据拷贝到原生内存，返回对应布局的 MemorySegment。 */
        private MemorySegment allocateData(Arena a, InputTensor t) {
            return switch (t.type()) {
                case FLOAT -> {
                    float[] arr = (float[]) t.data();
                    MemorySegment m = a.allocate(FLOAT, arr.length);
                    m.copyFrom(MemorySegment.ofArray(arr));
                    yield m;
                }
                case INT64 -> {
                    long[] arr = (long[]) t.data();
                    MemorySegment m = a.allocate(LONG, arr.length);
                    m.copyFrom(MemorySegment.ofArray(arr));
                    yield m;
                }
                case FLOAT16 -> {
                    short[] arr = (short[]) t.data();
                    MemorySegment m = a.allocate(SHORT, arr.length);
                    m.copyFrom(MemorySegment.ofArray(arr));
                    yield m;
                }
            };
        }

        /** 读取一个输出 OrtValue 的形状与浮点数据：按元素类型读取，FP16 自动转为 float。 */
        private Output readOutput(MemorySegment value, Arena arena) throws Throwable {
            MemorySegment infoOut = arena.allocate(ADDRESS);
            check((MemorySegment) getTensorTypeAndShape.invoke(value, infoOut));
            MemorySegment info = infoOut.get(ADDRESS, 0L);
            int elementType;
            long[] shape;
            long total;
            try {
                MemorySegment typeOut = arena.allocate(INT);
                check((MemorySegment) getTensorElementType.invoke(info, typeOut));
                elementType = typeOut.get(INT, 0L);

                MemorySegment cntOut = arena.allocate(LONG);
                check((MemorySegment) getDimensionsCount.invoke(info, cntOut));
                int dimCount = (int) cntOut.get(LONG, 0L);
                MemorySegment dims = arena.allocate(LONG, dimCount);
                check((MemorySegment) getDimensions.invoke(info, dims, (long) dimCount));
                shape = new long[dimCount];
                total = 1L;
                for (int i = 0; i < dimCount; i++) {
                    shape[i] = dims.get(LONG, (long) i * LONG.byteSize());
                    total *= shape[i];
                }
            } finally {
                releaseTensorTypeAndShapeInfo.invoke(info);
            }
            MemorySegment dataPtrOut = arena.allocate(ADDRESS);
            check((MemorySegment) getTensorMutableData.invoke(value, dataPtrOut));
            MemorySegment dataPtr = dataPtrOut.get(ADDRESS, 0L);
            float[] out = readFloats(dataPtr, total, elementType);
            return new Output(out, shape);
        }

        /** 按元素类型从原生内存读取浮点数据：FLOAT 直读，FLOAT16 经半精度转换，其余报错。 */
        private float[] readFloats(MemorySegment dataPtr, long total, int elementType) {
            return switch (elementType) {
                case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT ->
                        dataPtr.reinterpret(total * 4L).toArray(FLOAT);
                case ONNX_TENSOR_ELEMENT_DATA_TYPE_FLOAT16 -> {
                    short[] half = dataPtr.reinterpret(total * 2L).toArray(SHORT);
                    float[] f = new float[half.length];
                    for (int i = 0; i < half.length; i++) {
                        f[i] = halfToFloat(half[i]);
                    }
                    yield f;
                }
                default -> throw new OnnxException("不支持的输出张量元素类型: " + elementType);
            };
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                releaseSession.invoke(session);
            } catch (Throwable t) {
                throw new OnnxException("释放会话失败", t);
            }
        }
    }

    @Override
    public void close() {
        persistent.close();
    }
}
