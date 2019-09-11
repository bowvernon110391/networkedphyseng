package app;

import shared.Shared;
import gameServer.PhysEngServer;

import com.bowie.javagl.Matrix4;
import com.bowie.javagl.Vector3;
import com.jogamp.nativewindow.WindowClosingProtocol.WindowClosingMode;
import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.event.WindowListener;
import com.jogamp.newt.event.WindowUpdateEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.glu.gl2.GLUgl2;
import com.jogamp.opengl.util.Animator;

public class GuiServerApp implements GLEventListener {
	
	boolean forward, backward, right, left;
	boolean spin;
	
	float camAspect = 1.f;
	
	PhysEngServer server;
	
	float camXZRot = 0;
	float camYRot = 30;
	float camDist = 20;
	Matrix4 projMat = new Matrix4();
	
	public GuiServerApp(String serverAddr, int serverPort) {
		server = new PhysEngServer(serverAddr, serverPort);
	}

	public void startApp() {
		server.runServerThread();
		
		Display disp = NewtFactory.createDisplay(null);
		Screen scr = NewtFactory.createScreen(disp, 0);
		GLProfile glProfile = GLProfile.getDefault();
		GLCapabilities glCap = new GLCapabilities(glProfile);
		final GLWindow glWindow = GLWindow.create(scr, glCap);
		
		glWindow.setSize(640, 480);
		glWindow.setUndecorated(false);
		glWindow.setPointerVisible(true);
		glWindow.confinePointer(false);
		glWindow.setTitle("PhysEngine SERVER");

		glWindow.setVisible(true);
		glWindow.setDefaultCloseOperation(WindowClosingMode.DISPOSE_ON_CLOSE);
		
		glWindow.addGLEventListener(this);
		
glWindow.addWindowListener(new WindowListener() {
			
			public void windowResized(WindowEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			public void windowRepaint(WindowUpdateEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			public void windowMoved(WindowEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			public void windowLostFocus(WindowEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			public void windowGainedFocus(WindowEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			public void windowDestroyed(WindowEvent e) {
				// TODO Auto-generated method stub
				System.exit(0);
			}
			
			public void windowDestroyNotify(WindowEvent e) {
				// TODO Auto-generated method stub
				
			}
		});

		glWindow.addKeyListener(new KeyListener() {
			
			@Override
			public void keyReleased(KeyEvent e) {
				switch (e.getKeyCode()) {
				case KeyEvent.VK_SPACE:
					synchronized (server.world) {
						Shared.addClientBody(server.world, null);
					}
					break;
				case KeyEvent.VK_ESCAPE:
					glWindow.destroy();
					break;
				}
			}
			
			@Override
			public void keyPressed(KeyEvent e) {
				switch (e.getKeyCode()) {
				case KeyEvent.VK_LEFT:
					camXZRot -= 10;
					break;
				case KeyEvent.VK_RIGHT:
					camXZRot += 10;
					break;
				case KeyEvent.VK_UP:
					camYRot += 10;
					camYRot = Math.min(89, camYRot);
					break;
				case KeyEvent.VK_DOWN:
					camYRot -= 10;
					camYRot = Math.max(-89, camYRot);
					break;
				
				}
				
			}
		});

		Animator anim = new Animator(glWindow);
		anim.setRunAsFastAsPossible(true);
		anim.start();
	}
	
	public static void main(String [] args) {
		String serverAddr = args.length >= 1 ? args[0] : "localhost";
		int serverPort = args.length >= 2 ? Integer.parseInt(args[1]) : 3232;
		
		GuiServerApp app = new GuiServerApp(serverAddr, serverPort);
		
		app.startApp();
	}

	@Override
	public void init(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		
		gl.glClearColor(0, 0, .1f, 1);
		gl.glEnable(GL2.GL_DEPTH_TEST);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {		
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		GL2 gl = drawable.getGL().getGL2();
		
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT|GL2.GL_DEPTH_BUFFER_BIT);
		
		Matrix4.perspective(90.f, camAspect, 1.f, 1000.f, projMat);
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glLoadMatrixf(projMat.m, 0);
		
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// set camera here
		GLU glu = GLUgl2.createGLU();
		
		// compute camera
		Vector3 camTarget = new Vector3();
		
		float sinXZ = (float) Math.sin(Math.toRadians(camXZRot));
		float cosXZ = (float) Math.cos(Math.toRadians(camXZRot));
		float cosY = (float) Math.cos(Math.toRadians(camYRot));
		float sinY = (float) Math.sin(Math.toRadians(camYRot));
		Vector3 camPos = new Vector3(
				camDist * sinXZ * cosY,
				camDist * sinY,
				camDist * cosXZ * cosY
				);
		
		Vector3.add(camPos, camTarget, camPos);
		
		glu.gluLookAt(camPos.x, camPos.y, camPos.z, 
				camTarget.x, camTarget.y, camTarget.z, 
				0, 1, 0);
		
		// draw world
		synchronized (server.world) {
			server.world.debugDraw(gl, true, false, false, false, 0);
		}
		
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width,
			int height) {
		camAspect = (float)width/height;
	}
}
