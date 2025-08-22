package com.dooji.dno.track;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.*;

public class RoutePathfinder {
    
    public static List<TrackSegment> generatePathFromRoute(World world, Route route, TrackSegment sidingTrack) {
        if (route == null || sidingTrack == null || route.getStationIds().isEmpty()) {
            return new ArrayList<>();
        }
        
        Map<String, TrackSegment> allTracks = TrackManager.getTracksFor(world);
        if (allTracks.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<TrackSegment> path = new ArrayList<>();
        Set<TrackSegment> visited = new HashSet<>();
        
        TrackSegment current = sidingTrack;
        path.add(current);
        visited.add(current);
        
        List<String> stationIds = route.getStationIds();
        for (String stationId : stationIds) {
            TrackSegment stationTrack = findStationTrack(world, stationId);
            if (stationTrack != null) {
                List<TrackSegment> pathToStation = findPathAlongTracks(current, stationTrack, allTracks, visited);
                if (!pathToStation.isEmpty()) {
                    path.addAll(pathToStation);
                    current = stationTrack;
                    visited.addAll(pathToStation);
                }
            }
        }
        
        return path;
    }
    
    private static TrackSegment findStationTrack(World world, String stationId) {
        Map<String, TrackSegment> allTracks = TrackManager.getTracksFor(world);
        for (TrackSegment segment : allTracks.values()) {
            if ("platform".equals(segment.getType()) && stationId.equals(segment.getStationId())) {
                return segment;
            }
        }

        return null;
    }
    
    private static List<TrackSegment> findPathAlongTracks(TrackSegment start, TrackSegment end, Map<String, TrackSegment> allTracks, Set<TrackSegment> visited) {
        if (start.equals(end)) {
            return new ArrayList<>();
        }
        
        List<TrackSegment> path = new ArrayList<>();
        TrackSegment current = start;
        
        while (current != null && !current.equals(end)) {
            TrackSegment next = findNextTrackTowards(current, end, allTracks, visited);
            if (next == null || next.equals(current)) {
                break;
            }
            
            path.add(next);
            visited.add(next);
            current = next;
        }
        
        return path;
    }
    
    private static TrackSegment findNextTrackTowards(TrackSegment current, TrackSegment target, Map<String, TrackSegment> allTracks, Set<TrackSegment> visited) {
        BlockPos currentEnd = current.end();
        BlockPos currentStart = current.start();
        
        List<TrackSegment> candidates = new ArrayList<>();
        
        for (TrackSegment other : allTracks.values()) {
            if (other.equals(current) || visited.contains(other)) {
                continue;
            }
            
            if (currentEnd.equals(other.start()) || currentEnd.equals(other.end()) ||
                currentStart.equals(other.start()) || currentStart.equals(other.end())) {
                
                candidates.add(other);
            }
        }
        
        if (candidates.isEmpty()) {
            return null;
        }
        
        candidates.sort((a, b) -> {
            double distanceA = getDistanceToTarget(a, target);
            double distanceB = getDistanceToTarget(b, target);
            
            if (Math.abs(distanceA - distanceB) < 2.0) {
                return Double.compare(getTrackAlignmentScore(a, current), getTrackAlignmentScore(b, current));
            }
            
            return Double.compare(distanceA, distanceB);
        });
        
        return candidates.get(0);
    }
    
    private static double getTrackAlignmentScore(TrackSegment track, TrackSegment previous) {
        BlockPos prevEnd = previous.end();
        BlockPos prevStart = previous.start();
        
        if (track.start().equals(prevEnd) || track.end().equals(prevEnd)) {
            return 1.0;
        } else if (track.start().equals(prevStart) || track.end().equals(prevStart)) {
            return 0.5;
        }
        
        return 0.0;
    }
    
    private static double getDistanceToTarget(TrackSegment track, TrackSegment target) {
        BlockPos trackCenter = new BlockPos(
            (track.start().getX() + track.end().getX()) / 2,
            (track.start().getY() + track.end().getY()) / 2,
            (track.start().getZ() + track.end().getZ()) / 2
        );
        
        BlockPos targetCenter = new BlockPos(
            (target.start().getX() + target.end().getX()) / 2,
            (target.start().getY() + target.end().getY()) / 2,
            (target.start().getZ() + target.end().getZ()) / 2
        );
        
        return Math.sqrt(
            Math.pow(trackCenter.getX() - targetCenter.getX(), 2) +
            Math.pow(trackCenter.getY() - targetCenter.getY(), 2) +
            Math.pow(trackCenter.getZ() - targetCenter.getZ(), 2)
        );
    }
    
    public static List<Vec3d> buildContinuousPoints(List<TrackSegment> segments) {
        List<Vec3d> points = new ArrayList<>();
        BlockPos currentEndpoint = null;

        for (TrackSegment segment : segments) {
            List<Vec3d> forwardPoints = getSegmentPoints(segment);
            List<Vec3d> chosen = forwardPoints;

            if (currentEndpoint == null) {
                currentEndpoint = segment.end();
            } else {
                boolean connectsAtStart = segment.start().equals(currentEndpoint);
                boolean connectsAtEnd = segment.end().equals(currentEndpoint);

                if (connectsAtStart) {
                    chosen = forwardPoints;
                    currentEndpoint = segment.end();
                } else if (connectsAtEnd) {
                    List<Vec3d> reversed = new ArrayList<>(forwardPoints);
                    Collections.reverse(reversed);

                    chosen = reversed;
                    currentEndpoint = segment.start();
                } else {
                    Vec3d last = points.isEmpty() ? null : points.get(points.size() - 1);
                    if (last != null) {
                        Vec3d startCenter = new Vec3d(segment.start().getX() + 0.5, segment.start().getY(), segment.start().getZ() + 0.5);
                        Vec3d endCenter = new Vec3d(segment.end().getX() + 0.5, segment.end().getY(), segment.end().getZ() + 0.5);

                        double ds = last.distanceTo(startCenter);
                        double de = last.distanceTo(endCenter);

                        if (de < ds) {
                            List<Vec3d> reversed = new ArrayList<>(forwardPoints);
                            Collections.reverse(reversed);

                            chosen = reversed;
                            currentEndpoint = segment.start();
                        } else {
                            chosen = forwardPoints;
                            currentEndpoint = segment.end();
                        }
                    } else {
                        currentEndpoint = segment.end();
                    }
                }
            }

            for (Vec3d p : chosen) {
                if (points.isEmpty() || !points.get(points.size() - 1).equals(p)) {
                    points.add(p);
                }
            }
        }

        return points;
    }
    
    private static List<Vec3d> getSegmentPoints(TrackSegment segment) {
        List<Vec3d> points = new ArrayList<>();
        
        double totalLength = Math.sqrt(
            Math.pow(segment.end().getX() - segment.start().getX(), 2) +
            Math.pow(segment.end().getY() - segment.start().getY(), 2) +
            Math.pow(segment.end().getZ() - segment.start().getZ(), 2)
        );

        double startAngle = Math.atan2(segment.startDirection().getOffsetZ(), segment.startDirection().getOffsetX());
        double endAngle = Math.atan2(segment.endDirection().getOffsetZ(), segment.endDirection().getOffsetX());

        double angleDifference = endAngle - startAngle;
        while (angleDifference > Math.PI) angleDifference -= 2 * Math.PI;
        while (angleDifference < -Math.PI) angleDifference += 2 * Math.PI;

        boolean tangentsColinear = segment.startDirection() == segment.endDirection() || segment.startDirection() == segment.endDirection().getOpposite();
        boolean axisAligned;

        if (segment.startDirection().getAxis() == Direction.Axis.X) {
            axisAligned = Math.abs((segment.end().getZ() + 0.5) - (segment.start().getZ() + 0.5)) < 1e-6;
        } else {
            axisAligned = Math.abs((segment.end().getX() + 0.5) - (segment.start().getX() + 0.5)) < 1e-6;
        }
        
        boolean isStraightTrack = tangentsColinear && axisAligned;

        if (isStraightTrack) {
            points.addAll(generateStraightTrackPoints(segment, totalLength));
        } else {
            points.addAll(generateCurvedTrackPoints(segment, totalLength, angleDifference));
        }

        return points;
    }
    
    private static List<Vec3d> generateStraightTrackPoints(TrackSegment segment, double totalLength) {
        List<Vec3d> points = new ArrayList<>();
        double stepSize = 0.5;

        double startCenterX = segment.start().getX() + 0.5;
        double startCenterZ = segment.start().getZ() + 0.5;
        double endCenterX = segment.end().getX() + 0.5;
        double endCenterZ = segment.end().getZ() + 0.5;
        double startY = segment.start().getY();
        double endY = segment.end().getY();

        for (double distanceAlongPath = 0.0; distanceAlongPath <= totalLength; distanceAlongPath += stepSize) {
            double parameterT = totalLength > 1e-9 ? distanceAlongPath / totalLength : 0.0;
            double verticalT = applyVerticalEase(parameterT, segment.getSlopeCurvature());

            double x = startCenterX + (endCenterX - startCenterX) * parameterT;
            double z = startCenterZ + (endCenterZ - startCenterZ) * parameterT;
            double y = startY + (endY - startY) * verticalT;

            points.add(new Vec3d(x, y, z));
        }

        return points;
    }
    
    private static List<Vec3d> generateCurvedTrackPoints(TrackSegment segment, double totalLength, double angleDifference) {
        List<Vec3d> points = new ArrayList<>();

        double startCenterX = segment.start().getX() + 0.5;
        double startCenterZ = segment.start().getZ() + 0.5;
        double endCenterX = segment.end().getX() + 0.5;
        double endCenterZ = segment.end().getZ() + 0.5;
        double startY = segment.start().getY();
        double endY = segment.end().getY();

        double deltaAbsX = Math.abs(endCenterX - startCenterX);
        double deltaAbsZ = Math.abs(endCenterZ - startCenterZ);

        Vec3d startDirectionVec = new Vec3d(segment.startDirection().getOffsetX(), 0, segment.startDirection().getOffsetZ()).normalize();
        Vec3d endDirectionVec = new Vec3d(segment.endDirection().getOffsetX(), 0, segment.endDirection().getOffsetZ()).normalize();

        boolean hasStartAxis = segment.startDirection().getAxis() != null;
        boolean hasEndAxis = segment.endDirection().getAxis() != null;
        
        double startAxisDistance = hasStartAxis ? Math.max(deltaAbsX, deltaAbsZ) : Math.min(deltaAbsX, deltaAbsZ);
        double endAxisDistance = hasEndAxis ? Math.max(deltaAbsX, deltaAbsZ) : Math.min(deltaAbsX, deltaAbsZ);
        double circleApproxFactor = 0.55228477;

        double startControlDist = startAxisDistance * circleApproxFactor;
        double endControlDist = endAxisDistance * circleApproxFactor;

        double control1X = startCenterX + startDirectionVec.x * startControlDist;
        double control1Z = startCenterZ + startDirectionVec.z * startControlDist;
        double control2X = endCenterX - endDirectionVec.x * endControlDist;
        double control2Z = endCenterZ - endDirectionVec.z * endControlDist;

        int sampleCount = 200;
        double[] sampledX = new double[sampleCount + 1];
        double[] sampledZ = new double[sampleCount + 1];
        double[] sampledY = new double[sampleCount + 1];
        double[] cumulativeDistances = new double[sampleCount + 1];

        sampledX[0] = startCenterX;
        sampledZ[0] = startCenterZ;
        sampledY[0] = startY;
        cumulativeDistances[0] = 0.0;

        for (int i = 1; i <= sampleCount; i++) {
            double parameterT = (double) i / sampleCount;
            double omt = 1.0 - parameterT;
            double bezierX = omt * omt * omt * startCenterX + 3 * omt * omt * parameterT * control1X + 3 * omt * parameterT * parameterT * control2X + parameterT * parameterT * parameterT * endCenterX;
            double bezierZ = omt * omt * omt * startCenterZ + 3 * omt * omt * parameterT * control1Z + 3 * omt * parameterT * parameterT * control2Z + parameterT * parameterT * parameterT * endCenterZ;
            double verticalT = applyVerticalEase(parameterT, segment.getSlopeCurvature());
            double bezierY = startY + (endY - startY) * verticalT;

            sampledX[i] = bezierX;
            sampledZ[i] = bezierZ;
            sampledY[i] = bezierY;
            double segLen = Math.sqrt((sampledX[i] - sampledX[i - 1]) * (sampledX[i] - sampledX[i - 1]) + (sampledZ[i] - sampledZ[i - 1]) * (sampledZ[i] - sampledZ[i - 1]) + (sampledY[i] - sampledY[i - 1]) * (sampledY[i] - sampledY[i - 1]));
            cumulativeDistances[i] = cumulativeDistances[i - 1] + segLen;
        }

        double stepSize = 0.5;
        for (double distanceAlongPath = 0.0; distanceAlongPath <= cumulativeDistances[sampleCount] + 1e-6; distanceAlongPath += stepSize) {
            int i = 1;

            while (i <= sampleCount && cumulativeDistances[i] < distanceAlongPath) i++;
            if (i > sampleCount) i = sampleCount;

            int i0 = i - 1;
            double seg = cumulativeDistances[i] - cumulativeDistances[i0];
            double alpha = seg > 1e-6 ? (distanceAlongPath - cumulativeDistances[i0]) / seg : 0.0;
            double x = sampledX[i0] + (sampledX[i] - sampledX[i0]) * alpha;
            double y = sampledY[i0] + (sampledY[i] - sampledY[i0]) * alpha;
            double z = sampledZ[i0] + (sampledZ[i] - sampledZ[i0]) * alpha;
            points.add(new Vec3d(x, y, z));
        }

        return points;
    }
    
    private static double applyVerticalEase(double t, double curvature) {
        double tt = Math.max(0.0, Math.min(1.0, t));
        double smooth = tt * tt * (3.0 - 2.0 * tt);
        double smoother = tt * tt * tt * (tt * (tt * 6 - 15) + 10);
        double c = Math.max(-1.0, Math.min(1.0, curvature));

        if (c < 0.0) {
            double a = -c;
            return smooth * (1.0 - a) + tt * a;
        } else if (c > 0.0) {
            double a = c;
            return smooth * (1.0 - a) + smoother * a;
        } else {
            return smooth;
        }
    }
}
