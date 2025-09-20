package com.example.MailSafe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@SpringBootApplication
public class MailSafeApplication {
	public static final Logger logger = LoggerFactory.getLogger(MailSafeApplication.class);
	public static void main(String[] args) {
		ConfigurableApplicationContext con = SpringApplication.run(MailSafeApplication.class, args);
		logger.info("MailSafe is running");
		//Establish and verify database connection
		establishDatabaseConnection(con);
		//定期清理数据库中僵尸任务：PENDING，FAILED，1小时后清除，10分钟检测一次
		while (true){
			//获取mail_tasks表所有PENDING，FAILED状态的邮件任务
			 if(LocalDateTime.now().getMinute()%10 == 0){
				 try{
					 JdbcTemplate template = con.getBean(JdbcTemplate.class);
					 template.update("DELETE FROM mail_tasks WHERE status IN ('PENDING', 'FAILED') AND created_at < ?",
							 LocalDateTime.now().minusHours(1));
					 logger.info("Cleaned up old PENDING and FAILED mail tasks");
					 template.update("DELETE FROM attachments WHERE mail_task_id NOT IN (SELECT id FROM mail_tasks)");
					 logger.info("Cleaned up old attachments");
				 }
				 catch(Exception e){
					 logger.error("Error during cleanup: " + e.getMessage());
				 }
			 }
		}
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
