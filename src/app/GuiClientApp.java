package app;


import java.io.IOException;

import shared.BodyState;
import shared.RenderState;
import networkMessages.MsgInput;
import gameServer.PhysEngClient;

import com.bowie.javagl.MathHelper;
import com.bowie.javagl.Matrix4;
import com.bowie.javagl.Polytope;
import com.bowie.javagl.Quaternion;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Shape;
import com.bowie.javagl.Vector3;
import com.jogamp.nativewindow.WindowClosingProtocol.WindowClosingMode;
import com.jogamp.newt.Display;
import com.jogamp.newt.NewtFactory;
import com.jogamp.newt.Screen;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
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
import com.jogamp.opengl.util.FPSAnimator;

public class GuiClientApp {
	
	public PhysEngClient client;
	public float elapsed = 0;
	public long lastTick = System.nanoTime(), currTick;
	
	public float timestep = 1.f/30;
	public float connectTimer = 0;
	
	
	public boolean forward = false, backward = false, left = false, right = false;
	public boolean spin = false, reset = false;
	
	float targetXZRot = 0;
	float targetYRot = 30;
	float targetCamDist = 20;
	
	float camXZRot = targetXZRot;
	float camYRot = targetYRot;
	float camDist = 20;
	
	
	
	float camAspect = 1;
	float camFOV = 60;
	Matrix4 projMat = new Matrix4();
	Vector3 camTarget = new Vector3();
	
	
	public GuiClientApp(String addr, int port) {
		client = new gameServer.PhysEngClient(addr, port);
	}
	
	public void update(float dt) {
		// attempt connection
		if (!client.isConnected()) {
			connectTimer -= dt;
			if (connectTimer <= 0) {
				connectTimer = 5;
				try {
					System.out.println("Attemtping to connect...");
					client.attemptConnect();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		} 
		
		// first, read message
		client.readMessages();
		
		// do update if possible
		if (client.initialized) {
			
			// compute force data
			float sinom = (float) Math.sin(Math.toRadians(camXZRot));
			float cosom = (float) Math.cos(Math.toRadians(camXZRot));
			
			Vector3 forwardDir = new Vector3(sinom, 0, cosom);
			Vector3 sideDir = new Vector3(cosom, 0, -sinom);
			
			float forwardMag = 0;
			float sideMag = 0;
			
			if (forward)
				forwardMag -= 900;
			if (backward)
				forwardMag += 900;
			
			if (right)
				sideMag += 900;
			if (left)
				sideMag -= 900;
			
			// compute force
			Vector3 f = new Vector3();
			forwardDir.scale(forwardMag);
			sideDir.scale(sideMag);
			
			Vector3.add(forwardDir, sideDir, f);
			
			// sample input
			MsgInput inp = new MsgInput();
			
			if (spin)
				inp.setKeyPress(MsgInput.KEY_JUMP);
			inp.setForce(f);
			
			// apply it?
			synchronized (client.world) {
				client.tick(inp, dt);
			}
		}
		
	}
	
	public void init(GL2 gl) {
		gl.glEnable(GL2.GL_DEPTH_TEST);
		gl.glClearColor(0, .1f, .05f, 1);
	}
	
	public void render(GL2 gl, float dt) {
		// clear buffer and render shit
		gl.glClear(GL2.GL_COLOR_BUFFER_BIT|GL2.GL_DEPTH_BUFFER_BIT);
		
		gl.glMatrixMode(GL2.GL_PROJECTION);
		Matrix4.perspective(camFOV, camAspect, 1.f, 1000.f, projMat);
		gl.glLoadMatrixf(projMat.m, 0);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glLoadIdentity();
		
		// compute camera
		
		
//		synchronized (client.world) {
//			if (client.initialized)
//				camTarget.setTo(client.body.getPos());
//		}
		
		// match target
		camDist += (targetCamDist - camDist) * 12.f * dt;
		camXZRot += (targetXZRot - camXZRot) * 12.f * dt;
		camYRot += (targetYRot - camYRot) * 12.f * dt;
		
		
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
		
		GLU glu = GLUgl2.createGLU();
		
		glu.gluLookAt(camPos.x, camPos.y, camPos.z, 
				camTarget.x, camTarget.y, camTarget.z, 
				0, 1, 0);
		
		
		if (client.world != null) {
			synchronized (client.world) {
//				client.world.debugDraw(gl, true, false, false, false, 0);
				// render fixed objects
				for (RigidBody b : client.world.getBodies()) {
					if (b.isFixed()) {
						float [] color = Polytope.getColor(b.getId());
						gl.glColor3fv(color, 0);
						b.debugDraw(gl, dt);
					}
				}
				
				
				// also, draw shit using previous pos
				for (RigidBody b : client.world.getBodies()) {
					if (b.isFixed())
						continue;
					
					BodyState bs = client.lastServerState.get(b.getId());
					
					if (bs != null) {
						// draw here
						Shape s = b.getShape();
						
						// draw it heheh unless we're sleeping
						if (!b.isSleeping()) {
							gl.glColor3f(.54f, .44f, .44f);
							s.render(gl, bs.pos, bs.rot);
						}
						
					}
				}
				
				// also draw the render object
				Vector3 tmpPos = new Vector3();
				Quaternion tmpRot = new Quaternion();
				
				for (RenderState rs : client.renderStates) {
					
					
					if (rs.body != null) {						
//						gl.glColor3f(.1f, 1.0f, 0);
						float [] color = Polytope.getColor(rs.state.id);
						gl.glColor3fv(color, 0);
						
						rs.calculateRenderData(tmpPos, tmpRot, dt);
						rs.body.getShape().render(gl, tmpPos, tmpRot);
						
						if (rs.state.id == client.bodyId) {
							camTarget.x += (tmpPos.x - camTarget.x) * 1.9f * dt;
							camTarget.y += (tmpPos.y - camTarget.y) * 1.9f * dt;
							camTarget.z += (tmpPos.z - camTarget.z) * 1.9f * dt;
						}
					}
				}
			}
		}
	}

	public static void main(String[] args) {
		String serverAddr = "localhost";
		int serverPort = 3232;
		
		if (args.length >= 1)
			serverAddr = args[0];
		if (args.length >= 2)
			serverPort = Integer.parseInt(args[1]);
		
		final GuiClientApp app = new GuiClientApp(serverAddr, serverPort);
		
		Display disp = NewtFactory.createDisplay(null);
		Screen scr = NewtFactory.createScreen(disp, 0);
		GLProfile glProfile = GLProfile.getDefault();
		GLCapabilities glCap = new GLCapabilities(glProfile);
		final GLWindow glWindow = GLWindow.create(scr, glCap);
		
		glWindow.setSize(640, 480);
		glWindow.setUndecorated(false);
		glWindow.setPointerVisible(true);
		glWindow.confinePointer(false);
		glWindow.setTitle("PhysEngine Client Test");
//		glWindow.setContextCreationFlags(GLContext.CTX_OPTION_DEBUG);
		glWindow.setVisible(true);
		glWindow.setDefaultCloseOperation(WindowClosingMode.DISPOSE_ON_CLOSE);
		
		glWindow.addMouseListener(new MouseListener() {
			int lastX = 0, lastY = 0;
			boolean tracking = false;
			
			@Override
			public void mouseWheelMoved(MouseEvent e) {
				float [] rot = e.getRotation();
				
				System.out.println(String.format("%.4f %.4f %.4f ROT", rot[0], rot[1], rot[2]));
				app.targetCamDist -= rot[1] * .5f;
				
				// clamp
				app.targetCamDist = MathHelper.clamp(app.targetCamDist, 1.f, 32.f);
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				tracking = false;
			}
			
			@Override
			public void mousePressed(MouseEvent e) {
				tracking = true;
			}
			
			@Override
			public void mouseMoved(MouseEvent e) {
				lastX = e.getX();
				lastY = e.getY();
			}
			
			@Override
			public void mouseExited(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseEntered(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
			
			@Override
			public void mouseDragged(MouseEvent e) {
				if (tracking) {
					int cx = e.getX();
					int cy = e.getY();
					
					app.targetXZRot -= (cx - lastX) * 2.f;
					app.targetYRot += (cy - lastY) * 2.f;
					
					lastX = cx;
					lastY = cy;
				}
			}
			
			@Override
			public void mouseClicked(MouseEvent e) {
				// TODO Auto-generated method stub
				
			}
		});
		
		glWindow.addKeyListener(new KeyListener() {
			
			public void keyReleased(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
					glWindow.destroy();
				
				if (e.isAutoRepeat())
					return;
				
				switch (e.getKeyCode()) {
				case KeyEvent.VK_LEFT:
					app.left = false;
					break;
				case KeyEvent.VK_RIGHT:
					app.right = false;
					break;
				case KeyEvent.VK_UP:
					app.forward = false;
					break;
				case KeyEvent.VK_DOWN:
					app.backward = false;
					break;
					
				case KeyEvent.VK_SPACE:
					app.spin = false;
					break;
				}
				
//				app.onKeyRelease(e.getKeyCode());
			}
			
			public void keyPressed(KeyEvent e) {
				if (e.isAutoRepeat())
					return;
				
//				app.onKeyPress(e.getKeyCode());
				switch (e.getKeyCode()) {
				case KeyEvent.VK_LEFT:
					app.left = true;
					break;
				case KeyEvent.VK_RIGHT:
					app.right = true;
					break;
				case KeyEvent.VK_UP:
					app.forward = true;
					break;
				case KeyEvent.VK_DOWN:
					app.backward = true;
					break;
					
				case KeyEvent.VK_SPACE:
					app.spin = true;
					break;
				}
			}
		});
		
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
		
		FPSAnimator anim = new FPSAnimator(glWindow, 60, true);
		anim.start();
		
		glWindow.addGLEventListener(new GLEventListener() {
			
			public void reshape(GLAutoDrawable drawable, int x, int y, int width,
					int height) {
				
//				app.onResize(x, y, width, height);
				app.camAspect = (float)width/height;
			}
			
			public void init(GLAutoDrawable drawable) {
//				app.onInit(drawable.getGL().getGL2());		
				app.init(drawable.getGL().getGL2());
			}
			
			public void dispose(GLAutoDrawable drawable) {
//				app.setRunState(false);
			}
			
			public void display(GLAutoDrawable drawable) {

				app.currTick = System.nanoTime();
				app.elapsed += (app.currTick - app.lastTick) / 1000000000.f;
				app.lastTick = app.currTick;
				
				while (app.elapsed >= app.timestep) {
					app.elapsed -= app.timestep;
					app.update(app.timestep);
				}
				
				// now render
				app.render(drawable.getGL().getGL2(), app.elapsed);
			}
		});
	}

}
