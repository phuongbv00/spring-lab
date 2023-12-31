package com.example.gateway.resolver.token;

import com.example.gateway.authentication.DefaultUserDetails;
import com.example.gateway.service.AuthServiceClient;
import com.example.gateway.service.payload.ExceptionRes;
import com.example.gateway.service.payload.UserDetailsRes;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
public class DistributedTokenResolver implements TokenResolver {
    private final AuthServiceClient authServiceClient;
    private final ObjectMapper objectMapper;

    public DistributedTokenResolver(AuthServiceClient authServiceClient,
                                    ObjectMapper objectMapper) {
        this.authServiceClient = authServiceClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String extract(ServerHttpRequest request) throws TokenResolveException {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new TokenResolveException("Invalid AUTHORIZATION header");
        }
        return authHeader;
    }

    @Override
    public UserDetails resolve(String token) throws TokenResolveException {
        try {
            UserDetailsRes res = authServiceClient.getUserDetails(token).data();
            var authorities = res.permissions().stream()
                    .map(UserDetailsRes.Permission::code)
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            return new DefaultUserDetails(res.id(), res.username(), res.isActive(), authorities);
        } catch (FeignException e) {
            log.error(e.getMessage());
            log.error("FeignException->contentUTF8: {}", e.contentUTF8());
            var msg = Optional.ofNullable(e.contentUTF8())
                    .filter(StringUtils::hasText)
                    .map(content -> readValue(content, ExceptionRes.class))
                    .map(ExceptionRes::message)
                    .orElse(e.getMessage());
            throw new TokenResolveException(msg, e);
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new TokenResolveException(e.getMessage(), e);
        }
    }

    @SneakyThrows
    private <T> T readValue(String content, Class<T> clazz) {
        return objectMapper.readValue(content, clazz);
    }
}
