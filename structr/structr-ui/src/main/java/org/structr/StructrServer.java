package org.structr;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;

import org.eclipse.jetty.server.Connector;

/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
 */
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import org.structr.context.ApplicationContextListener;
import org.structr.rest.servlet.JsonRestServlet;
import org.structr.web.servlet.HtmlServlet;
import org.structr.websocket.servlet.WebSocketServlet;

import org.tuckey.web.filters.urlrewrite.UrlRewriteFilter;

//~--- JDK imports ------------------------------------------------------------

import java.io.File;

import java.util.*;

import javax.servlet.DispatcherType;

//~--- classes ----------------------------------------------------------------

/**
 * structr UI server
 *
 * @author Axel Morgner
 */
public class StructrServer {

	public static void main(String[] args) throws Exception {

		String appName        = "structr UI 0.4.8";
		String host           = System.getProperty("host", "0.0.0.0");
		int port              = Integer.parseInt(System.getProperty("port", "8080"));
		int maxIdleTime       = Integer.parseInt(System.getProperty("maxIdleTime", "30000"));
		int requestHeaderSize = Integer.parseInt(System.getProperty("requestHeaderSize", "8192"));
		String contextPath    = System.getProperty("contextPath", "/");

		System.out.println();
		System.out.println("Starting " + appName + " (host=" + host + ":" + port + ", maxIdleTime=" + maxIdleTime + ", requestHeaderSize=" + requestHeaderSize + ")");
		System.out.println();

		Server server                     = new Server(port);
		HandlerCollection handlers        = new HandlerCollection();
		ContextHandlerCollection contexts = new ContextHandlerCollection();

		// ServletContextHandler context0    = new ServletContextHandler(ServletContextHandler.SESSIONS);
		SelectChannelConnector connector0 = new SelectChannelConnector();

		connector0.setHost(host);
		connector0.setPort(port);
		connector0.setMaxIdleTime(maxIdleTime);
		connector0.setRequestHeaderSize(requestHeaderSize);

		String basePath = System.getProperty("home", "");
		File baseDir    = new File(basePath);

		basePath = baseDir.getAbsolutePath();

		// baseDir  = new File(basePath);
		System.out.println("Starting in directory " + basePath);

		String modulesPath = basePath + "/modules";
		File modulesDir    = new File(modulesPath);

		if (!modulesDir.exists()) {

			modulesDir.mkdir();

		}

		modulesPath = modulesDir.getAbsolutePath();

		File modulesConfFile     = new File(modulesPath + "/modules.conf");
		String warPath           = StructrServer.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		List<String> modulesConf = new LinkedList<String>();

		if (!(warPath.endsWith(".war"))) {

			File warFile = new File(warPath + "..");

			warPath = warFile.getAbsolutePath() + "/structr-ui.war";

		}

		WebAppContext webapp = new WebAppContext();

		webapp.setDescriptor(webapp + "/WEB-INF/web.xml");
		webapp.setTempDirectory(baseDir);
		webapp.setWar(warPath);
		System.out.println("Using WAR file " + warPath);

		FilterHolder rewriteFilter = webapp.addFilter(UrlRewriteFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD));

		// rewriteFilter.setInitParameter("logLevel", "DEBUG");
		// Strange behaviour of jetty:
		// If there's a directory with the same name like the WAR file in the same directory,
		// jetty will extract the classes into this directory and not into 'webapp'.
		// To avoid this, we will check for this directory and exit if it exists.
		String directoryName            = warPath.substring(0, (warPath.length() - 4));
		File directoryWhichMustNotExist = new File(directoryName);

		if (directoryWhichMustNotExist.exists()) {

			System.err.println("Directory " + directoryWhichMustNotExist + " must not exist.");

			// System.err.println("Delete it or move WAR file to another directory and start from there.");
			// System.exit(1);
			directoryWhichMustNotExist.delete();
			System.err.println("Deleted " + directoryWhichMustNotExist);

		}

		// search structr modules in WAR file
		ZipFile war                          = new ZipFile(new File(warPath));
		Enumeration<ZipArchiveEntry> entries = war.getEntries();

		while (entries.hasMoreElements()) {

			ZipArchiveEntry entry = entries.nextElement();
			String name           = entry.getName().substring(entry.getName().lastIndexOf("/") + 1);

			if (name.startsWith("structr") && name.endsWith(".jar")) {

				System.out.println("Found module " + name);
				modulesConf.add(name + "=active");

			}

		}

		war.close();

		// Create modules.conf if not existing
		if (!modulesConfFile.exists()) {

			modulesConfFile.createNewFile();
			FileUtils.writeLines(modulesConfFile, "UTF-8", modulesConf);

		}

		String confPath = basePath + "/structr.conf";
		File confFile   = new File(confPath);

		// Create structr.conf if not existing
		if (!confFile.exists()) {

			// synthesize a config file
			List<String> config = new LinkedList<String>();

			config.add("##################################");
			config.add("# structr global config file     #");
			config.add("##################################");
			config.add("# local title of running structr application");
			config.add("application.title = structr UI (" + host + ")");
			config.add("# base directory");
			config.add("base.path = " + basePath);
			config.add("# temp files directory");
			config.add("tmp.path = /tmp");
			config.add("# database files directory");
			config.add("database.path = " + basePath + "/db");
			config.add("# binary files directory");
			config.add("files.path = " + basePath + "/files");
			config.add("# modules directory");
			config.add("modules.path = " + basePath + "/modules");
			config.add("smtp.host = localhost");
			config.add("smtp.port = 25");
			config.add("superuser.username = admin");
			config.add("superuser.password = admin");
			config.add("configured.services = ModuleService NodeService AgentService");

			// don't start cron service without config file
			// config.add("configured.services = ModuleService NodeService AgentService CronService");
			// config.add("CronService.tasks = " + FeedCrawlerTask.class.getName());
//                      config.add(FeedCrawlerTask.class.getName() + ".cronExpression = 0 0 * * * *");
			confFile.createNewFile();
			FileUtils.writeLines(confFile, "UTF-8", config);
		}

		webapp.setInitParameter("configfile.path", confFile.getAbsolutePath());
		webapp.setContextPath(contextPath);
		webapp.setParentLoaderPriority(true);

		// JSON REST Servlet
		JsonRestServlet structrRestServlet = new JsonRestServlet();
		ServletHolder holder               = new ServletHolder(structrRestServlet);
		Map<String, String> initParams     = new HashMap<String, String>();

		initParams.put("RequestLogging", "true");
		initParams.put("PropertyFormat", "FlatNameValue");
		initParams.put("ResourceProvider", "org.structr.rest.resource.StructrResourceProvider");
		initParams.put("Authenticator", "org.structr.core.auth.StructrAuthenticator");
		initParams.put("DefaultPropertyView", "default");
		initParams.put("IdProperty", "uuid");
		holder.setInitParameters(initParams);
		holder.setInitOrder(2);
		webapp.addServlet(holder, "/structr/rest/*");
		webapp.addEventListener(new ApplicationContextListener());

		// HTML Servlet
		HtmlServlet htmlServlet            = new HtmlServlet();
		ServletHolder htmlServletHolder    = new ServletHolder(htmlServlet);
		Map<String, String> htmlInitParams = new HashMap<String, String>();

		htmlInitParams.put("Authenticator", "org.structr.core.auth.StructrAuthenticator");
		htmlInitParams.put("IdProperty", "uuid");
		htmlServletHolder.setInitParameters(htmlInitParams);
		htmlServletHolder.setInitOrder(3);
		webapp.addServlet(htmlServletHolder, "/structr/html/*");
		webapp.addEventListener(new ApplicationContextListener());

		// WebSocket Servlet
		WebSocketServlet wsServlet       = new WebSocketServlet();
		ServletHolder wsServletHolder    = new ServletHolder(wsServlet);
		Map<String, String> wsInitParams = new HashMap<String, String>();

		wsInitParams.put("Authenticator", "org.structr.core.auth.StructrAuthenticator");
		wsInitParams.put("IdProperty", "uuid");
		wsServletHolder.setInitParameters(wsInitParams);
		wsServletHolder.setInitOrder(4);
		webapp.addServlet(wsServletHolder, "/structr/ws/*");
		webapp.addEventListener(new ApplicationContextListener());

		// enable request logging
		RequestLogHandler requestLogHandler = new RequestLogHandler();
		String logPath                      = basePath + "/logs";
		File logDir                         = new File(logPath);

		logPath = logDir.getAbsolutePath();

		// Create logs directory if not existing
		if (!logDir.exists()) {

			logDir.mkdir();

		}

		NCSARequestLog requestLog = new NCSARequestLog(logPath + "/structr-yyyy_mm_dd.request.log");

		requestLog.setRetainDays(90);
		requestLog.setAppend(true);
		requestLog.setExtended(false);
		requestLog.setLogTimeZone("GMT");
		requestLogHandler.setRequestLog(requestLog);

//              contexts.setHandlers(new Handler[] { context0, webapp, requestLogHandler });
		contexts.setHandlers(new Handler[] { webapp, requestLogHandler });
		handlers.setHandlers(new Handler[] { contexts, new DefaultHandler(), requestLogHandler });
		server.setHandler(handlers);
		server.setConnectors(new Connector[] { connector0 });
		server.setGracefulShutdown(1000);
		server.setStopAtShutdown(true);
		System.out.println();
		System.out.println("structr UI:        http://" + host + ":" + port + contextPath);
		System.out.println();
		server.start();
		server.join();
		System.out.println(appName + " stopped.");
	}
}