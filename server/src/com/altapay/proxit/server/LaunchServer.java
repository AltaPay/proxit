package com.altapay.proxit.server;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.altapay.proxit.server.clients.ProxitServer;

public class LaunchServer
{

	public static void main(String[] args) throws Exception
	{
		Properties properties = loadJarProperties();
		
		ProxitServerContainer container = ProxitServerContainer.initialize(new PropertiesProxitConfig(properties));

		// Listen for clients
		ProxitServer server = container.getProxitServer();
		server.start();
	}

	private static Properties loadJarProperties() throws IOException
	{
		Properties properties = new Properties();
		InputStream propStream = LaunchServer.class.getResourceAsStream("/META-INF/context.properties");
		if(propStream != null)
		{
			properties.load(propStream);
		}
		return properties;
	}
}
