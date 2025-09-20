package com.example.MailSafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.context.ConfigurableApplicationContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MailSafeApplication {
	public static final Logger logger = LoggerFactory.getLogger(MailSafeApplication.class);
	public static void main(String[] args){
		ConfigurableApplicationContext con = SpringApplication.run(MailSafeApplication.class, args);
		logger.info("MailSafe is running");
		//Establish and verify database connection
		establishDatabaseConnection(con);
	}
	private static void establishDatabaseConnection(ConfigurableApplicationContext con) {
		try{
			JdbcTemplate tem = con.getBean(JdbcTemplate.class);
			String res = tem.queryForObject("SELECT 1", String.class);
			logger.info("Database connection established: " + res);
		}
		catch(Exception e){
			logger.error("Database connection failed: " + e.getMessage());
			con.close();
			throw e;
		}
	}
}
