package com.pachiraframework.watchdog.kafka.consumer;

import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;

import com.pachiraframework.watchdog.kafka.consumer.data.FileBeatLogMessage;
import com.pachiraframework.watchdog.kafka.consumer.data.LogMessage;
import com.pachiraframework.watchdog.kafka.consumer.data.hadler.LogMessageHandlerChain;

import io.krakens.grok.api.Match;

/**
 * 应用日志监听，日志中的log4j.xml配置如下：
 * <pre>
 * {@code
 * 	<appender name="file-log" class="org.apache.log4j.DailyRollingFileAppender">
 * 		<param name="file" value="order-service.log" />
 * 		<param name="append" value="true" />
 * 		<param name="DatePattern" value="'.'yyyy-MM-dd" />
 * 		<layout class="org.apache.log4j.PatternLayout">
 * 			<param name="ConversionPattern" value="[%t] %d %-5p [%c.%M(%L)] %m%n" />
 * 		</layout>
 * 	</appender>
 *  }
 *  </pre>
 *  日志示例：<br/>
 *  <pre>
 *  [main] 2018-07-07 10:31:12,138 INFO  [org.springframework.context.support.ClassPathXmlApplicationContext.prepareRefresh(503)] Refreshing org.springframework.context.support.ClassPathXmlApplicationContext@7e9a5fbe: startup date [Sat Jul 07 10:31:12 CST 2018]; root of context hierarchy
 *  </pre>
 *  topic中的数据示例：<br/>
 *  <pre>
 *  {"@timestamp":"2018-07-07T02:46:46.780Z","@metadata":{"beat":"filebeat","type":"doc","version":"6.3.0","topic":"lxw1234"},"prospector":{"type":"log"},"input":{"type":"log"},"beat":{"name":"01AD58697812703","hostname":"01AD58697812703","version":"6.3.0"},"host":{"name":"01AD58697812703"},"source":"D:\\home\\admin\\output\\logs\\javalog\\order-service.log","offset":1366406,"message":"[main] 2018-07-07 10:46:41,641 INFO  [org.springframework.context.support.ClassPathXmlApplicationContext.prepareRefresh(503)] Refreshing org.springframework.context.support.ClassPathXmlApplicationContext@7e9a5fbe: startup date [Sat Jul 07 10:46:41 CST 2018]; root of context hierarchy"}
 *  </pre>
 * @author kevin wang
 */
public class Log4jFormatLogConsumer extends AbstractKafkaConsumer{
	private static final String PATTERN = "\\[%{NOTSPACE:thread}\\]%{SPACE}%{TIMESTAMP_ISO8601:timestamp}%{SPACE}%{LOGLEVEL:level}%{SPACE}\\[%{NOTSPACE:location}\\]%{SPACE}%{ANYTHING:message}";
	@Autowired
	private LogMessageHandlerChain logMessageHandlerChain;
	
	public void listen(ConsumerRecord<Integer, String> cr) throws Exception {
		LogMessage logMessage = convertToLogMessage(cr);
		handleLogMessage(logMessage);
	}
	
	/**
	 * 转化成LogMessage的后续处理措施
	 * @param logMessage
	 */
	protected void handleLogMessage(LogMessage logMessage) {
		logMessageHandlerChain.handle(logMessage);
	}
	
	/**
	 * 将kafka消息中的内容转化为统一格式的LogMessage对象
	 * @param cr
	 * @return
	 */
	protected LogMessage convertToLogMessage(ConsumerRecord<Integer, String> cr) {
		FileBeatLogMessage fileBeatLogMessage = consumeFileBeatLogMessage(cr);
		Match match = getGrok().match(fileBeatLogMessage.getMessage());
		final Map<String, Object> capture = match.capture();
		LogMessage logMessage = buildLogMessage(capture);
		logMessage.setHost(fileBeatLogMessage.getFields()==null?fileBeatLogMessage.getBeat().getHostname():fileBeatLogMessage.getFields().get("ip"));
		logMessage.setAppId(fileBeatLogMessage.getFields()== null?"NOT_PROVIDED":fileBeatLogMessage.getFields().get("app_id"));
		return logMessage;
	}
	
	
	private LogMessage buildLogMessage(Map<String, Object> capture) {
		String level = (String)capture.get("level");
		String message = (String)capture.get("message");
//		String message = null;
//		List<String> messages = (List<String>)capture.get("message");
//		if(messages!=null && !messages.isEmpty()) {
//			message = messages.get(0);
//		}
		String timestamp = (String)capture.get("timestamp");
		String location = (String)capture.get("location");
		LogMessage logMessage = new LogMessage();
		logMessage.setLevel(level);
		logMessage.setLocation(location);
		logMessage.setTimestamp(timestamp);
		logMessage.setMessage(message);
		return logMessage;
	}

	@Override
	protected String matchPattern() {
		return PATTERN;
	}
}
