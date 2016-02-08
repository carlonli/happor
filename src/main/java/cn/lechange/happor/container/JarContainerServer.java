package cn.lechange.happor.container;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.springframework.context.support.FileSystemXmlApplicationContext;

import cn.lechange.happor.HapporContext;
import cn.lechange.happor.HapporWebserver;
import cn.lechange.happor.WebserverHandler;
import cn.lechange.happor.context.HapporMultipleContext;
import cn.lechange.happor.springtags.HapporServerElement;

public class JarContainerServer {
	
	private static Logger logger = Logger.getLogger(JarContainerServer.class);

	private HapporWebserver server;
	private String containerConfig;
	private String log4jConfig;
	private List<ContainerPath> pathList = new ArrayList<ContainerPath>();
	
	public JarContainerServer(String filename) {
		FileSystemXmlApplicationContext serverContext = new FileSystemXmlApplicationContext(filename);
		HapporServerElement element = serverContext.getBean(HapporServerElement.class);
		server = element.getServer();
		containerConfig = element.getConfigs().get("container");
		log4jConfig = element.getConfigs().get("log4j");
		serverContext.close();
	}
	
	private void readContainerConfig(String filename) {
		FileInputStream in = null;
		try {
			in = new FileInputStream(filename);
			Properties prop = new Properties();
			prop.load(in);
			String container = prop.getProperty("container");
			if (container != null) {
				String[] list = container.split(",");
				for (String name : list) {
					ContainerPath c = new ContainerPath();
					c.setPath(prop.getProperty("." + name + ".path"));
					c.setJar(prop.getProperty("." + name + ".jar"));
					pathList.add(c);
				}
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					
				}
			}
		}
	}
	
	public void load() {
		readContainerConfig(containerConfig);
		HapporMultipleContext context = new HapporMultipleContext();
		for (ContainerPath container : pathList) {
			String path = container.getPath();
			String jar = container.getJar();
			logger.info("config path[" +path  + "] " + jar);
			HapporContext ctx = JarImporter.load(jar);
			if (ctx == null) {
				logger.error("load fail: " + jar);
			} else {
				if (path.isEmpty()) {
					context.setDefault(ctx);
				} else {
					context.addPath(path, ctx);
				}
			}
		}
		context.printInfo();
		context.applyServer(server);
		
		WebserverHandler handler = context.getWebserverHandler();
		if (handler != null) {
			handler.onInit(server);
		}
	}

	public void start() {
		if(log4jConfig != null) {
			PropertyConfigurator.configure(log4jConfig);
		}
		if (containerConfig == null) {
			logger.error("no container!");
			return;
		}
		Timer timer = new Timer();  
        timer.schedule(checkTask, 1000, 1000);
		server.startup();
	}
	
	private TimerTask checkTask = new TimerTask() {

		private long lastTime = 0;

		@Override
		public void run() {
			// TODO Auto-generated method stub
			File file = new File(containerConfig);
			long nowTime = file.lastModified();
			if (lastTime != nowTime) {
				logger.warn("container config[" + containerConfig + "] changed!");
				load();
				lastTime = nowTime;
			}
		}
		
	};
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		JarContainerServer server = new JarContainerServer("conf/containerServer.xml");
		server.start();
	}

}
