package com.message.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.message.database.RoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;

@Configuration
@EnableTransactionManagement(order = Ordered.LOWEST_PRECEDENCE - 1)
public class DataSourceConfig {

	private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.source.hikari")
	public DataSource sourceDataSource() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}

	@Bean
	@ConfigurationProperties(prefix = "spring.datasource.replica.hikari")
	public DataSource replicaDataSource() {
		return DataSourceBuilder.create().type(HikariDataSource.class).build();
	}

	@Bean
	public DataSource routingDataSource(
		@Qualifier("sourceDataSource") DataSource sourceDataSource,
		@Qualifier("replicaDataSource") DataSource replicaDataSource) throws SQLException {
		RoutingDataSource routingDataSource = new RoutingDataSource();

		Map<Object, Object> targetDataSources = new HashMap<>();
		targetDataSources.put("source", sourceDataSource);
		targetDataSources.put("replica", replicaDataSource);

		routingDataSource.setTargetDataSources(targetDataSources);
		routingDataSource.setDefaultTargetDataSource(sourceDataSource);

		// 명시적으로 서버가 뜰 때 최소한 커넥션풀은 준비해놓기 위해 replica의 커넥션을 준비하고 바로 버리기 처리
		try (Connection connection = replicaDataSource.getConnection()) {
			log.info("Init ReplicaConnectionPool");
		}

		return routingDataSource;
	}

	/*
	  - 스프링부트가 동작할 때 커넥션을 먼저 얻고 트랜잭션을 세팅하여 read only가 원하는 타이밍에 설정되지 않고 있음
	  - 따라서 쿼리가 실행될 때까지 커넥션 획득을 미루고 트랜잭션이 세팅된 후 커넥션을 획득하도록 설정함
	  - 하지만 이와 같이 설정을 하게 되면 스프링부트가 동작할 때 초기화가 안되고 최초 접근할 때 초기화되기 때문에
	    운영환경에서는 트래픽이 처음 들어왔을 때 커넥션 풀을 초기화하면 초반 트래픽에 대해서 타임아웃이 발생하다
	    정상적으로 처리되는데까지 시간이 소요된다
	  - 대부분 라이브 서비스들은 웜업 단계를 거치고 웜업 단계에서 대부분 이런 문제가 해결되기는 하는데
	    그렇게 사용하는 방법이 있고 아예 명시적으로 서버가 뜰 때 최소한 커넥션풀은 준비해놓는 상태로 띄워두는게 좋다
	 */
	@Primary
	@Bean
	public DataSource lazyConnectionDataSource(@Qualifier("routingDataSource") DataSource routingDataSource) {
		return new LazyConnectionDataSourceProxy(routingDataSource);
	}
}
