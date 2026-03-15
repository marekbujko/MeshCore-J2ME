/*
 * Copyright (C) 2011 OnlineCity
 * Licensed under the MIT license, which can be read at: http://www.opensource.org/licenses/mit-license.php
 */

package dk.onlinecity.qrr.client;

import javax.microedition.lcdui.Alert;
import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;

public class CameraCanvas extends Canvas
{
	private static CameraCanvas instance = null;
	private QrScanListener listener;
	private Display display;
	private InitCamera initCamera = null;
	private String message = "Opening camera...";
	private boolean captureRequested = false;

	private CameraCanvas(QrScanListener listener, Display display)
	{
		setFullScreenMode(true);
		setTitle("Scan QR Code");
		this.listener = listener;
		this.display = display;
	}

	public static CameraCanvas getInstance(QrScanListener listener, Display display)
	{
		if (instance == null) {
			instance = new CameraCanvas(listener, display);
		} else {
			// Reuse the same canvas but update listener/display for new scan sessions
			instance.listener = listener;
			instance.display = display;
		}
		if (instance.isShown()) instance.repaint();
		return instance;
	}

	protected void showNotify()
	{
		// Reset capture flag and (re)initialize camera whenever this
		// canvas becomes visible, so subsequent scans don't reuse
		// an old frozen frame.
		captureRequested = false;
		initCamera = new InitCamera();
		new Thread(initCamera).start();
	}

	protected void paint(Graphics g)
	{
		g.setColor(0x000000);
		g.fillRect(0, 0, getWidth(), getHeight());
		g.setColor(0xffffff);
		g.setFont(Font.getFont(Font.FACE_PROPORTIONAL, Font.STYLE_PLAIN, Font.SIZE_MEDIUM));
		g.drawString(message, getWidth() >> 1, getHeight() >> 1, Graphics.BOTTOM | Graphics.HCENTER);
	}

	protected void keyPressed(int keyCode)
	{
		if (captureRequested) return;
		switch (keyCode) {
		case 0: break;
		case -7: case -8: case -11: // soft keys / back
			Alert exitAlert = new Alert("", "Exit QR Scanner?", null, AlertType.CONFIRMATION);
			exitAlert.addCommand(new Command("OK", Command.OK, 1));
			exitAlert.addCommand(new Command("Cancel", Command.CANCEL, 1));
			exitAlert.setCommandListener(new CommandListener() {
				public void commandAction(Command cmd, Displayable d) {
					if (cmd.getCommandType() == Command.OK) listener.exit();
					else listener.showCamera();
				}
			});
			display.setCurrent(exitAlert);
			break;
		default:
			captureRequested = true;
			listener.takeSnapshot();
		}
	}

	protected void pointerPressed(int x, int y) {
		if (captureRequested) return;
		captureRequested = true;
		listener.takeSnapshot();
	}

	private void setMessage(String message)
	{
		this.message = message;
	}

	class InitCamera implements Runnable
	{
		public void run()
		{
			CameraControl.getInstance();
			setMessage("Tap or press key to scan");
			listener.showCamera();
		}
	}
}
