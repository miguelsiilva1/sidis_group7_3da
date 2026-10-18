package aisafe.shared.infrastructure.peers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Set;

/**
 * Lists the peers that did not answer while a response was being built in
 * {@code X-Unreachable-Peers}, and marks the response as partial ({@code X-Partial-Result: true})
 * when some data may be missing because of them.
 */
@ControllerAdvice
public class PartialResultAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpRequest = servletRequest.getServletRequest();
            Set<String> unreachable = UnreachablePeers.of(httpRequest);
            if (!unreachable.isEmpty() && !response.getHeaders().containsHeader(PeerHeaders.UNREACHABLE_PEERS)) {
                response.getHeaders().set(PeerHeaders.UNREACHABLE_PEERS, String.join(",", unreachable));
                if (UnreachablePeers.mayBeIncomplete(httpRequest)) {
                    response.getHeaders().set(PeerHeaders.PARTIAL_RESULT, "true");
                }
            }
        }
        return body;
    }
}
