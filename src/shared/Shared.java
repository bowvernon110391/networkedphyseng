package shared;

import com.bowie.javagl.Box;
import com.bowie.javagl.Physics;
import com.bowie.javagl.Quaternion;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

public class Shared {
	final public static Box unitBox = new Box(1, 1, 1);
	
	public static RigidBody addClientBody(Physics world, BodyState state) {
		RigidBody b = new RigidBody(100, unitBox);
		
		b.setPos(new Vector3(0, 10, 0));
		b.setCcdRadius(0.1f);
		b.setContinuous(true);
		b.setFriction(.8f);
		b.setRestitution(.6f);
		
		// set id if forceId is positive too		
		// set to state if not null
		if (state != null) {
			b.setId(state.id);
			b.setPos(state.pos);
			b.setRot(state.rot);
			b.setVel(state.vel);
			b.setAngVel(state.angVel);
		}
		
		// add to world
		world.addBody(b);
		
		return b;
	}
	
	public static void initPhysicsWorld(Physics world) {
		float worldSize = 50.f;
		RigidBody.resetGUID();
		
		Quaternion zRot = Quaternion.makeAxisRot(new Vector3(0,0,1), (float) Math.toRadians(90));
		Quaternion xRot = Quaternion.makeAxisRot(new Vector3(1,0,0), (float) Math.toRadians(90));
		
		Box ybox = new Box(worldSize, 1, worldSize);
		
		world.setGravity(new Vector3(0, -9.8f, 0));
		
		world.setAngularDamping(.085f);
		world.setLinearDamping(.075f);
		
		
		// bottom box
		RigidBody bA = new RigidBody(-1.0f, ybox);
		bA.setFriction(.94f);
		bA.setPos(new Vector3(0, -2, 0));
		bA.setFixed(true);
		bA.setRestitution(.5f);
		
		world.addBody(bA);	
		
		// right box
		bA = new RigidBody(-1.f, ybox);
		bA.setFriction(.94f);
		bA.setPos(new Vector3(worldSize/2-.5f, worldSize/2-2, 0));
		bA.setFixed(true);
		bA.setRestitution(.5f);
		bA.setRot(zRot);
		
		world.addBody(bA);
		
		// left box
		bA = new RigidBody(-1.f, ybox);
		bA.setFriction(.94f);
		bA.setPos(new Vector3(-worldSize/2-.5f, worldSize/2-2, 0));
		bA.setFixed(true);
		bA.setRestitution(.5f);
		bA.setRot(zRot);
		
		world.addBody(bA);
		
		// north box
		bA = new RigidBody(-1.f, ybox);
		bA.setFriction(.94f);
		bA.setPos(new Vector3(0, worldSize/2-2, worldSize/2-.5f));
		bA.setFixed(true);
		bA.setRestitution(.5f);
		bA.setRot(xRot);
		
		world.addBody(bA);
		
		// north box
		bA = new RigidBody(-1.f, ybox);
		bA.setFriction(.94f);
		bA.setPos(new Vector3(0, worldSize/2-2, -worldSize/2-.5f));
		bA.setFixed(true);
		bA.setRestitution(.5f);
		bA.setRot(xRot);
		
		world.addBody(bA);
	}
}
