package com.traveler.gateway.support;

import java.net.InetSocketAddress;
import java.util.List;
import org.springframework.http.server.reactive.ServerHttpRequest;

public final class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    // 인스턴스화 방지
    private ClientIpResolver() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /** ALB가 붙인 값(X-Forwarded-For의 마지막 값)을 쓴다. 앞쪽 값은 클라이언트가 위조할 수 있다. */
    public static String resolve(ServerHttpRequest request) {
        List<String> forwarded = request.getHeaders().getOrEmpty(X_FORWARDED_FOR);
        if (!forwarded.isEmpty()) {
            String[] ips = forwarded.get(forwarded.size() - 1).split(",");
            String last = ips[ips.length - 1].trim();
            if (!last.isEmpty()) {
                return last;
            }
        }
        InetSocketAddress remote = request.getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "-";
    }
}
