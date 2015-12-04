package com.altapay.proxit.client;

import java.awt.GridLayout;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import sun.awt.VerticalBagLayout;

public class LaunchClient
{
	public static void main(String[] args) throws Exception
	{
		ProxitClientContainer container = ProxitClientContainer.initialize();

		OptionPaneTest ui = new OptionPaneTest();
		ui.show();
		
		ProxitClient client = container.getProxitClient(ui.getLocalPort(), ui.getRemotePort(), ui.getRemoteHost());
		
		client.start();
	}

	public static class OptionPaneTest
	{
		JPanel myPanel = new JPanel(new GridLayout(3, 2));
		JTextField hostField = new JTextField(20);
		JTextField portField = new JTextField(10);
		JTextField localPortField = new JTextField(10);

		
		public OptionPaneTest()
		{
			myPanel.add(new JLabel("Remote Host"));
			hostField.setText("shipall24.devaltapaysecure.dk");
			myPanel.add(hostField);
			
			myPanel.add(new JLabel("Remote Port"));
			portField.setHorizontalAlignment(JLabel.RIGHT);
			portField.setText("8089");
			myPanel.add(portField);
			
			myPanel.add(new JLabel("Local Port"));
			localPortField.setText("7089");
			localPortField.setHorizontalAlignment(JLabel.RIGHT);
			myPanel.add(localPortField);
		}
		
		public void show()
		{
			JOptionPane.showMessageDialog(null, myPanel);
		}
		
		public String getRemoteHost()
		{
			return hostField.getText();
		}
		
		public short getRemotePort()
		{
			return Short.parseShort(portField.getText());
		}

		public short getLocalPort()
		{
			return Short.parseShort(localPortField.getText());
		}
	}
}
