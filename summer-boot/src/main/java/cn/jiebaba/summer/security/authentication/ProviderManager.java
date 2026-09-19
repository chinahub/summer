package cn.jiebaba.summer.security.authentication;

import cn.jiebaba.summer.security.core.Authentication;

import java.util.List;

/**
 * 将认证委托给一组 {@link AuthenticationProvider} 链，返回首个成功结果。对应 {@code ProviderManager}。
 */
public class ProviderManager implements AuthenticationManager {

    private final List<AuthenticationProvider> providers;

    public ProviderManager(AuthenticationProvider... providers) {
        this.providers = List.of(providers);
    }

    public ProviderManager(List<AuthenticationProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        AuthenticationException last = null;
        for (AuthenticationProvider provider : providers) {
            if (!provider.supports(authentication.getClass())) continue;
            try {
                return provider.authenticate(authentication);
            } catch (AuthenticationException e) {
                last = e;
            }
        }
        if (last != null) throw last;
        throw new AuthenticationException("No AuthenticationProvider supported " + authentication.getClass().getName());
    }

    public List<AuthenticationProvider> getProviders() {
        return providers;
    }
}
