package ch.alchemists.jbmesh.util;

import ch.alchemists.jbmesh.data.property.Vec3Attribute;
import ch.alchemists.jbmesh.operator.normalgen.NewellNormal;
import ch.alchemists.jbmesh.structure.Face;
import ch.alchemists.jbmesh.structure.Vertex;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector2f;
import com.jme3.math.Vector3f;
import java.util.Iterator;

public class PlanarCoordinateSystem {
    private static final float AXIS_LENGTH_EPSILON = 0.001f;
    private static final float AXIS_LENGTH_EPSILON_SQUARED = AXIS_LENGTH_EPSILON * AXIS_LENGTH_EPSILON;

    private static final float MIN_VERTEX_DISTANCE = 0.00001f;
    private static final float MIN_VERTEX_DISTANCE_SQUARED = MIN_VERTEX_DISTANCE * MIN_VERTEX_DISTANCE;


    private final Vector3f p = new Vector3f();
    private final Vector3f x = new Vector3f(1, 0, 0);
    private final Vector3f y = new Vector3f(0, 1, 0);


    public PlanarCoordinateSystem() {}


    private void validate() {
        if(Math.abs(1f - x.lengthSquared()) > AXIS_LENGTH_EPSILON_SQUARED)
            throw new IllegalArgumentException("Invalid X axis (normalized?)");
        if(Math.abs(1f - y.lengthSquared()) > AXIS_LENGTH_EPSILON_SQUARED)
            throw new IllegalArgumentException("Invalid Y axis (normalized?)");
    }



    public PlanarCoordinateSystem withX(Vector3f x, Vector3f n) {
        this.p.zero();
        this.x.set(x);
        this.y.set(n).crossLocal(x).normalizeLocal(); // TODO: Does it matter if x/y are not normalized?
        validate();
        return this;
    }

    public PlanarCoordinateSystem withXAt(Vector3f p, Vector3f x, Vector3f n) {
        this.p.set(p);
        this.x.set(x);
        this.y.set(n).crossLocal(x).normalizeLocal(); // TODO: Does it matter if x/y are not normalized?
        validate();
        return this;
    }

    public PlanarCoordinateSystem withXDifference(Vector3f xStart, Vector3f xEnd, Vector3f n) {
        if(xStart.isSimilar(xEnd, MIN_VERTEX_DISTANCE))
            throw new IllegalArgumentException("Distance between xStart and xEnd is too short");

        p.set(xStart);
        x.set(xEnd).subtractLocal(xStart).normalizeLocal();
        y.set(n).crossLocal(x).normalizeLocal();
        return this;
    }


    public PlanarCoordinateSystem withY(Vector3f y, Vector3f n) {
        this.p.zero();
        this.y.set(y);
        this.x.set(y).crossLocal(n).normalizeLocal(); // TODO: Does it matter if x/y are not normalized?
        validate();
        return this;
    }

    public PlanarCoordinateSystem withYAt(Vector3f p, Vector3f y, Vector3f n) {
        this.p.set(p);
        this.y.set(y);
        this.x.set(y).crossLocal(n).normalizeLocal(); // TODO: Does it matter if x/y are not normalized?
        validate();
        return this;
    }

    public PlanarCoordinateSystem withYDifference(Vector3f yStart, Vector3f yEnd, Vector3f n) {
        if(yStart.isSimilar(yEnd, MIN_VERTEX_DISTANCE))
            throw new IllegalArgumentException("Distance between yStart and yEnd is too short");

        p.set(yStart);
        y.set(yEnd).subtractLocal(yStart).normalizeLocal();
        x.set(y).crossLocal(n).normalizeLocal();
        return this;
    }


    public PlanarCoordinateSystem forFace(Face face, Vec3Attribute<Vertex> positions) {
        return forPolygon(face.vertices(), positions::get);
    }

    public <T> PlanarCoordinateSystem forPolygon(Iterable<T> elements, Func.MapVec3<T> positionMap) {
        Iterator<T> it = elements.iterator();
        if(!it.hasNext())
            throw new IllegalArgumentException("No elements.");

        Vector3f first = positionMap.get(it.next(), new Vector3f());
        Vector3f last  = first.clone();
        Vector3f valid = new Vector3f();
        Vector3f p     = new Vector3f();

        // Accumulate general direction of polygon
        Vector3f dirSum = new Vector3f();

        // Calculate face normal using Newell's Method
        Vector3f n = new Vector3f();

        int numVertices = 1;
        while(it.hasNext()) {
            positionMap.get(it.next(), p);
            NewellNormal.addToNormal(n, last, p);
            last.set(p);

            p.subtractLocal(first);
            dirSum.addLocal(p);

            // Count only vertices that are different from 'first'
            if(p.lengthSquared() > MIN_VERTEX_DISTANCE_SQUARED) {
                valid.set(p);
                numVertices++;
            }
        }

        if(numVertices < 3)
            throw new IllegalArgumentException("Cannot build PlanarCoordinateSystem with less than 3 valid positions.");

        // Add last segment from last to first
        NewellNormal.addToNormal(n, last, first);
        n.normalizeLocal();

        // Dir sum may be very near at 'first'. In this case use any valid vertex.
        if(dirSum.distanceSquared(first) <= MIN_VERTEX_DISTANCE_SQUARED)
            dirSum.set(valid).addLocal(first);

        withYDifference(first, dirSum, n);
        return this;
    }


    public Vector2f project(Vector3f v) {
        return project(v.x, v.y, v.z, new Vector2f());
    }

    public Vector2f project(Vector3f v, Vector2f store) {
        return project(v.x, v.y, v.z, store);
    }

    public Vector2f project(float vx, float vy, float vz) {
        return project(vx, vy, vz, new Vector2f());
    }

    public Vector2f project(float vx, float vy, float vz, Vector2f store) {
        Vector3f diff = new Vector3f(vx, vy, vz);
        diff.subtractLocal(p);

        store.x = diff.dot(x);
        store.y = diff.dot(y);

        return store;
    }


    public Vector3f unproject(Vector2f v) {
        return unproject(v.x, v.y, new Vector3f());
    }

    public Vector3f unproject(Vector2f v, Vector3f store) {
        return unproject(v.x, v.y, store);
    }

    public Vector3f unproject(float vx, float vy) {
        return unproject(vx, vy, new Vector3f());
    }

    public Vector3f unproject(float vx, float vy, Vector3f store) {
        // store = (v.x * x) + (v.y * y) + p
        store.x = (vx * x.x) + (vy * y.x);
        store.y = (vx * x.y) + (vy * y.y);
        store.z = (vx * x.z) + (vy * y.z);

        store.addLocal(p);
        return store;
    }


    public void rotate(float angleRad) {
        Quaternion rot = new Quaternion();
        rot.fromAngleAxis(angleRad, Vector3f.UNIT_Z);
        rot.multLocal(x);
        rot.multLocal(y);
    }


    @Override
    public String toString() {
        return "PlanarCoordinateSystem{x: " + x + " (" + x.length() + "), y: " + y + " (" + y.length() + "), p: " + p + "}";
    }
}
