package com.joshlong.mogul.api.utils.jdbc;

import com.joshlong.mogul.api.utils.RewindableResultSet;
import org.aopalliance.intercept.MethodInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactoryBean;

import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <strong>WARNING</strong>: instances of this class are <em>not</em> thread safe!!
 */
public abstract class RewindableResultSets {

	private static final Logger log = LoggerFactory.getLogger(RewindableResultSets.class);

	public static RewindableResultSet build(ResultSet resultSet) {
		try {
			var counter = new AtomicInteger();
			var replay = new AtomicBoolean(false);
			var map = new HashMap<Integer, Map<String, Object>>();
			var pfb = new ProxyFactoryBean();
			for (var c : Set.of(RewindableResultSet.class)) {
				pfb.addInterface(c);
			}
			pfb.setTarget(resultSet);
			pfb.addAdvice((MethodInterceptor) (invocation) -> {
				var row = counter.get();
				var methodName = invocation.getMethod().getName();
				var arguments = invocation.getArguments();
				var key = keyFor(invocation.getMethod(), arguments);

				if (methodName.equals("getClass")) {
					return Objects.requireNonNull(invocation.getThis()).getClass();
				}
				if (methodName.equals("next")) {
					counter.incrementAndGet();
					if (replay.get()) {
						return map.containsKey(counter.get());
					}
					return invocation.getMethod().invoke(resultSet, arguments);
				}

				if (methodName.equals("rewind")) {
					replay.set(true);
					counter.set(0);
					return null;
				}

				var rowMap = map.computeIfAbsent(row, _ -> new HashMap<>());
				log("Row: {}, Method: {}", row, methodName);
				var getter = methodName.startsWith("get");

				if (getter) {
					if (replay.get()) {
						log("replaying {}, returning cached value {}", methodName, rowMap.get(key));
						return rowMap.get(key);
					} //
					else {
						var data = invocation.getMethod().invoke(resultSet, arguments);
						rowMap.put(key, data);
						return data;
					}
				}
				log("invoking {} with arguments {}", methodName, Arrays.toString(arguments));
				return invocation.getMethod().invoke(resultSet, arguments);
			});
			return (RewindableResultSet) pfb.getObject();
		} //
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void log(String msg, Object... params) {
		if (true)
			return;
		if (log.isTraceEnabled()) {
			log.trace(msg, params);
		}
	}

	private static String keyFor(Method method, Object[] args) {
		return method.getName() + ":" + Arrays.toString(args);
	}

}
