package com.joshlong.mogul.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a tooken from an HTTP header and adds it to the details for the current
 * {@link org.springframework.security.core.Authentication authentication}.
 */
public class TokenPropagatingFilter extends OncePerRequestFilter {

	private final String tokenHeader;

	private final String detailsContextKey;

	public TokenPropagatingFilter(String tokenHeader, String detailsKey) {
		this.tokenHeader = tokenHeader;
		this.detailsContextKey = detailsKey;
	}

	@Override
	protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
			@NonNull FilterChain chain) throws ServletException, IOException {
		var wpToken = request.getHeader(this.tokenHeader);
		if (wpToken != null) {
			var authentication = SecurityContextHolder.getContext().getAuthentication();
			if (authentication instanceof AbstractAuthenticationToken token) {
				var details = new HashMap<String, Object>();
				// preserve existing details if any
				if (token.getDetails() instanceof Map<?, ?> existing) {
					existing.forEach((k, v) -> details.put(String.valueOf(k), v));
				}
				details.put(this.detailsContextKey, wpToken);
				token.setDetails(details);
			}
		}
		chain.doFilter(request, response);
	}

}
