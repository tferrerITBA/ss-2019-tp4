package ar.edu.itba.ss.tpe4;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.awt.geom.Point2D;

public class LennardJonesGasManager {
	
	private final Grid grid;
	private double balanceTime;
	private HashMap<Particle, Point2D.Double> positionMap;
	private List<Particle> previousParticles;

	public LennardJonesGasManager(Grid grid) {
		this.grid = grid;
		this.balanceTime = 0.0;
		this.positionMap = new HashMap<>();
		this.previousParticles = new ArrayList<>();
	}

	private double getParticleForce(double distance) {
		// Formula to get the force applied from one particle to another, extracted from the slides
		double coefficient = Configuration.GAS_L * Configuration.GAS_EPSILON / Configuration.GAS_Rm;
		double repulsion = Math.pow((Configuration.GAS_Rm / distance), Configuration.GAS_L + 1);
		double attraction = Math.pow((Configuration.GAS_Rm / distance), Configuration.GAS_J + 1);
		return - coefficient * (repulsion - attraction);
	}

	private Boolean isBalanced() {
		// returns true only if there are the same amount of particles in both chambers
		List<Particle> particles = grid.getParticles();

		Integer initialChamberAmount = 0;
		for (Particle particle: particles) {
			if (isInFirstChamber(particle)) {
				initialChamberAmount += 1;
			}
		}

		return Math.floor(initialChamberAmount - particles.size() / 2) == 0;
	}

	private double getTimeLimit() {
		// We have to handle different break conditions to allow for balance time exercises
		double timeLimit = Integer.MAX_VALUE;
		
		switch(Configuration.getTimeLimit()) {
			case -1:
				timeLimit = this.balanceTime > 0 ? this.balanceTime : Integer.MAX_VALUE;
				break;
			case -2:
				timeLimit = this.balanceTime > 0 ? this.balanceTime * 2 : Integer.MAX_VALUE;
				break;
			default:
				timeLimit = Configuration.getTimeLimit();
		}

		return timeLimit;
	}

	public Boolean isInFirstChamber(Particle particle) {
		return particle.getPosition().x < Configuration.GAS_BOX_SPLIT;
	}

	public void updatePositionByBouncing(List<Particle> particles) {
		// The particle has to bounce between the different walls
		for (Particle particle: particles) {
			Point2D.Double lastPosition = this.positionMap.get(particle);
			Boolean isOutsideTopBound = particle.getPosition().y > Configuration.GAS_BOX_HEIGHT;
			Boolean isOutsideBottomBound = particle.getPosition().y < 0;
			Boolean isOutsideRightBound = particle.getPosition().x > Configuration.GAS_BOX_WIDTH;
			Boolean isOutsideLeftBound = particle.getPosition().x < 0;
			Boolean isWithinHole = particle.getPosition().y > Configuration.GAS_BOX_HOLE_POSITION && particle.getPosition().y < Configuration.GAS_BOX_HOLE_POSITION + Configuration.GAS_BOX_HOLE_SIZE;
			double validDelta = 0.5 * Math.random() + 0.2;

			if (isOutsideTopBound) particle.setPosition(lastPosition.x, Configuration.GAS_BOX_HEIGHT - validDelta);
			if (isOutsideBottomBound) particle.setPosition(lastPosition.x, validDelta);
			if (isOutsideLeftBound) particle.setPosition(validDelta, lastPosition.y);
			if (isOutsideRightBound) particle.setPosition(Configuration.GAS_BOX_WIDTH - validDelta, lastPosition.y);

			// If the particle is in the area that's affected by the split
			if (!isWithinHole) {
				Boolean changedChamber = !isInFirstChamber(particle) && lastPosition.x < Configuration.GAS_BOX_SPLIT || isInFirstChamber(particle) && lastPosition.x > Configuration.GAS_BOX_SPLIT;
				// Only make it bounce if it CHANGED the chamber, but don't update the position in the position map
				// so we don't enter an endless loop.
				if (changedChamber) {
					particle.setPosition(lastPosition.x < Configuration.GAS_BOX_SPLIT ? lastPosition.x - validDelta : lastPosition.x + validDelta, particle.getPosition().y);
					continue;
				} 
			}

			this.positionMap.put(particle, (Point2D.Double) particle.getPosition().clone());
		}
	}

	public double distance(Particle a, Particle b) {
		// Calculate euclidean distance between a particle a and a particle b in 2D.
		return Math.sqrt(Math.pow(b.getPosition().x - a.getPosition().x, 2) + Math.pow(b.getPosition().y - a.getPosition().y, 2));
	}

	public List<Particle> getClosestParticles(Particle particle) {
		List<Particle> particles = this.previousParticles;
		// Get particles closer to the constant GAS_RANGE and larger than 0 (to avoid taking myself into account)
		// And only those that are in the same chamber
		return particles.stream().filter(p -> !particle.equals(p) && distance(particle, p) <= Configuration.GAS_RANGE && isInFirstChamber(particle) == isInFirstChamber(p)).collect(Collectors.toList());
	}

	private Point2D.Double getAppliedForce (Particle particle) {
		// First we get the particles that affect our motion (closer to GAS_RANGE distance)
		List<Particle> closeParticles = getClosestParticles(particle);
		List<Particle> wallColliders = getWallColiders(particle);
		double totalForceX = 0.0;
		double totalForceY = 0.0;

		// Then, we iterate over the closest particles, calculate the modulus of the force
		// then calculate the angle of the force (by using the position beteween the two particles)
		// then with that angle we calculate the components of the force in X and Y coordinates
		// and then we add that to the total force (for each component)
		for (Particle p: closeParticles) {
			double distance = Math.max(distance(p, particle), 0.75);
			double forceModulus = getParticleForce(distance);
			double forceAngle = Math.atan2(p.getPosition().y - particle.getPosition().y, p.getPosition().x - particle.getPosition().x);
			totalForceX += Math.cos(forceAngle) * forceModulus;
			totalForceY += Math.sin(forceAngle) * forceModulus;
		}

		// Same thing but with the particles that allow for wall collisions
		for (Particle wall: wallColliders) {
			double distance = Math.max(distance(wall, particle), 0.75);
			double forceModulus = getParticleForce(distance);
			double forceAngle = Math.atan2(wall.getPosition().y - particle.getPosition().y, wall.getPosition().x - particle.getPosition().x);
			totalForceX += Math.cos(forceAngle) * forceModulus;
			totalForceY += Math.sin(forceAngle) * forceModulus;
		}
		
		return new Point2D.Double(totalForceX, totalForceY);
	}

	private List<Particle> getWallColiders(Particle particle) {
		// Generate a list of particles that generate the force to make the particle collide with the wall.
		List<Particle> wallPoints = new ArrayList<>();

		wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(particle.getPosition().x, 0))); // bottom wall
		wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(particle.getPosition().x, Configuration.GAS_BOX_HEIGHT))); // top wall
		wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(0, particle.getPosition().y))); // left wall
		wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(Configuration.GAS_BOX_WIDTH, particle.getPosition().y))); // right wall
		if(particle.getPosition().y < Configuration.GAS_BOX_HOLE_POSITION) {
			wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(Configuration.GAS_BOX_SPLIT, particle.getPosition().y))); // bottom middle wall
		} else if (particle.getPosition().y > Configuration.GAS_BOX_HOLE_POSITION + Configuration.GAS_BOX_HOLE_SIZE) {
			wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(Configuration.GAS_BOX_SPLIT, particle.getPosition().y))); // top middle wall
		} else {
			wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(Configuration.GAS_BOX_SPLIT, Configuration.GAS_BOX_HOLE_POSITION))); // top corner middle wall
			wallPoints.add(new Particle(Configuration.GAS_PARTICLE_RADIUS, new Point2D.Double(Configuration.GAS_BOX_SPLIT, Configuration.GAS_BOX_HOLE_POSITION + Configuration.GAS_BOX_HOLE_SIZE))); // bottom corner middle wall
		}

		// Return only those close enough to collide
		return wallPoints.stream().filter(wall -> distance(particle, wall) <= Configuration.GAS_RANGE).collect(Collectors.toList());
	}

	private Point2D.Double getAppliedAcceleration (Particle particle) {
		// Divide each component of the force by the mass, and return that vector.
		Point2D.Double force = getAppliedForce(particle);
		return new Point2D.Double(force.x / particle.getMass(), force.y / particle.getMass());
	}

	public void verletUpdate(List<Particle> previousParticles) {
		// This is almost a true copy from the collider,
		// except we calculate a new position and a new velocity
		// for both X and Y coordinates
		List<Particle> currentParticles = grid.getParticles(); 
		
		for(int i = 0; i < currentParticles.size(); i++) {
			Particle currParticle = currentParticles.get(i);
			Particle prevParticle = previousParticles.get(i);
			Point2D.Double acceleration  = getAppliedAcceleration(currParticle);
			double newPositionX = 2 * currParticle.getPosition().getX() - prevParticle.getPosition().getX()
					+ Math.pow(Configuration.getTimeStep(), 2) * acceleration.x; //+error
			double newPositionY = 2 * currParticle.getPosition().getY() - prevParticle.getPosition().getY()
					+ Math.pow(Configuration.getTimeStep(), 2) * acceleration.y; //+error
			double newVelocityX = (newPositionX - prevParticle.getPosition().getX()) / (2 * Configuration.getTimeStep()); // + error
			double newVelocityY = (newPositionY - prevParticle.getPosition().getY()) / (2 * Configuration.getTimeStep()); // + error
			
			prevParticle.setPosition(currParticle.getPosition().getX(), currParticle.getPosition().getY());
			prevParticle.setVelocity(currParticle.getVelocity().getX(), currParticle.getVelocity().getY());
			if (newVelocityX == newVelocityY && newVelocityX == 0) {
				newVelocityX = currParticle.getVelocity().x;
				newVelocityY = currParticle.getVelocity().y;
			}
			currParticle.setPosition(newPositionX, newPositionY);
			currParticle.setVelocity(newVelocityX, newVelocityY);
		}
	}

	// Euler Algorithm evaluated in (- timeStep)
	private List<Particle> initPreviousParticles(List<Particle> currentParticles) {
		// This is almost a true copy from the collider,
		// except we calculate a previous position and a previous velocity
		// for both X and Y coordinates
		List<Particle> previousParticles = new ArrayList<>();
		for(Particle p : currentParticles) {
			Particle prevParticle = p.clone();
			Point2D.Double force = getAppliedForce(p);
			double prevPositionX = p.getPosition().getX() - Configuration.getTimeStep() * p.getVelocity().getX()
					+ Math.pow(Configuration.getTimeStep(), 2) * force.x / (2 * p.getMass()); // + error
			double prevPositionY = p.getPosition().getY() - Configuration.getTimeStep() * p.getVelocity().getY()
					+ Math.pow(Configuration.getTimeStep(), 2) * force.y / (2 * p.getMass()); // + error
			double prevVelocityX = p.getVelocity().getX() - (Configuration.getTimeStep() / p.getMass()) * force.x;// + error
			double prevVelocityY = p.getVelocity().getX() - (Configuration.getTimeStep() / p.getMass()) * force.y;// + error
			prevParticle.setPosition(prevPositionX, prevPositionY);
			prevParticle.setVelocity(prevVelocityX, prevVelocityY);
			previousParticles.add(prevParticle);
		}
		
		return previousParticles;
	}

	public double execute() {
		double accumulatedTime = 0.0; // s
		double animationOutputTimeLimit = 0.1; // s | 10fps
		double animationOutputTime = 0.0; // s
		this.previousParticles = initPreviousParticles(grid.getParticles());

		// load previous particles position
		for (Particle particle: this.previousParticles) {
			this.positionMap.put(particle, (Point2D.Double) particle.getPosition().clone());
		}

		while(Double.compare(accumulatedTime, getTimeLimit()) <= 0) {
			if (Configuration.isGasMode() && Double.compare(animationOutputTime, animationOutputTimeLimit) >= 0) {
				Configuration.writeGasOvitoOutputFile(accumulatedTime, grid.getParticles());
				animationOutputTime = 0.0;
			}
			

			// get balance time
			if (balanceTime == 0 && isBalanced()) {
				balanceTime = accumulatedTime;
				System.out.println("Hole Size: " + Configuration.GAS_BOX_HOLE_SIZE + "; Balance time: " + balanceTime);
				return balanceTime;
			}

			
			// increase time by dt
			accumulatedTime += Configuration.getTimeStep();
			animationOutputTime += Configuration.getTimeStep();
			
			// update position and velocity
			verletUpdate(previousParticles);

			// bound box
			updatePositionByBouncing(grid.getParticles());
		}
		return accumulatedTime;
	}

}
