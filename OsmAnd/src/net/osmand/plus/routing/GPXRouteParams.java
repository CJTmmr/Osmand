package net.osmand.plus.routing;

import net.osmand.GPXUtilities.GPXFile;
import net.osmand.GPXUtilities.Route;
import net.osmand.GPXUtilities.WptPt;
import net.osmand.Location;
import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.data.LocationPoint;
import net.osmand.data.WptLocationPoint;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.settings.backend.OsmandSettings;
import net.osmand.router.RouteSegmentResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static net.osmand.plus.routing.RouteProvider.collectSegmentPointsFromGpx;
import static net.osmand.router.RouteExporter.OSMAND_ROUTER_V2;

public class GPXRouteParams {

	private static final String OSMAND_ROUTER = "OsmAndRouter";

	protected List<LocationPoint> wpt;
	protected List<RouteSegmentResult> route;
	protected List<RouteDirectionInfo> directions;
	protected List<Location> points = new ArrayList<>();
	protected List<Location> segmentEndpoints = new ArrayList<>();
	protected List<WptPt> routePoints = new ArrayList<>();
	protected boolean reverse;
	protected boolean passWholeRoute;
	protected boolean calculateOsmAndRoute;
	protected boolean useIntermediatePointsRTE;
	protected boolean calculateOsmAndRouteParts;

	boolean addMissingTurns = true;

	public List<Location> getPoints() {
		return points;
	}

	public List<LocationPoint> getWpt() {
		return wpt;
	}

	public LatLon getLastPoint() {
		if (!points.isEmpty()) {
			Location l = points.get(points.size() - 1);
			return new LatLon(l.getLatitude(), l.getLongitude());
		}
		return null;
	}

	public GPXRouteParams prepareGPXFile(GPXRouteParamsBuilder builder) {
		GPXFile file = builder.file;
		reverse = builder.reverse;
		passWholeRoute = builder.passWholeRoute;
		calculateOsmAndRouteParts = builder.calculateOsmAndRouteParts;
		useIntermediatePointsRTE = builder.isUseIntermediatePointsRTE();
		builder.calculateOsmAndRoute = false; // Disabled temporary builder.calculateOsmAndRoute;
		if (!file.isPointsEmpty()) {
			wpt = new ArrayList<>(file.getPoints().size());
			for (WptPt w : file.getPoints()) {
				wpt.add(new WptLocationPoint(w));
			}
		}
		int selectedSegment = builder.getSelectedSegment();
		if (OSMAND_ROUTER_V2.equals(file.author)) {
			route = RouteProvider.parseOsmAndGPXRoute(points, file, segmentEndpoints, selectedSegment);
			if (selectedSegment == -1) {
				routePoints = file.getRoutePoints();
			} else {
				routePoints = file.getRoutePoints(selectedSegment);
			}
			if (reverse) {
				Collections.reverse(points);
				Collections.reverse(routePoints);
				Collections.reverse(segmentEndpoints);
			}
			addMissingTurns = route != null && route.isEmpty();
		} else if (file.isCloudmadeRouteFile() || OSMAND_ROUTER.equals(file.author)) {
			directions = RouteProvider.parseOsmAndGPXRoute(points, file, segmentEndpoints,
					OSMAND_ROUTER.equals(file.author), builder.leftSide, 10, selectedSegment);
			if (OSMAND_ROUTER.equals(file.author) && file.hasRtePt()) {
				// For files generated by OSMAND_ROUTER use directions contained unaltered
				addMissingTurns = false;
			}
			if (reverse) {
				// clear directions all turns should be recalculated
				directions = null;
				Collections.reverse(points);
				Collections.reverse(segmentEndpoints);
				addMissingTurns = true;
			}
		} else {
			// first of all check tracks
			if (!useIntermediatePointsRTE) {
				collectSegmentPointsFromGpx(file, points, segmentEndpoints, selectedSegment);
			}
			if (points.isEmpty()) {
				for (Route rte : file.routes) {
					for (WptPt pt : rte.points) {
						points.add(RouteProvider.createLocation(pt));
					}
				}
			}
			if (reverse) {
				Collections.reverse(points);
				Collections.reverse(segmentEndpoints);
			}
		}
		return this;
	}

	public static class GPXRouteParamsBuilder {

		private static final org.apache.commons.logging.Log log = PlatformUtil.getLog(GPXRouteParamsBuilder.class);

		boolean calculateOsmAndRoute = false;
		// parameters
		private final GPXFile file;
		private final boolean leftSide;
		private boolean reverse;
		private boolean passWholeRoute;
		private boolean calculateOsmAndRouteParts;
		private int selectedSegment = -1;

		public GPXRouteParamsBuilder(GPXFile file, OsmandSettings settings) {
			this.leftSide = settings.DRIVING_REGION.get().leftHandDriving;
			this.file = file;
		}

		public boolean isReverse() {
			return reverse;
		}

		public boolean isCalculateOsmAndRouteParts() {
			return calculateOsmAndRouteParts;
		}

		public void setCalculateOsmAndRouteParts(boolean calculateOsmAndRouteParts) {
			this.calculateOsmAndRouteParts = calculateOsmAndRouteParts;
		}

		public boolean isUseIntermediatePointsRTE() {
			return file.hasRtePt() && !file.hasTrkPt();
		}

		public boolean isCalculateOsmAndRoute() {
			return calculateOsmAndRoute;
		}

		public void setCalculateOsmAndRoute(boolean calculateOsmAndRoute) {
			this.calculateOsmAndRoute = calculateOsmAndRoute;
		}

		public int getSelectedSegment() {
			return selectedSegment;
		}

		public void setSelectedSegment(int selectedSegment) {
			this.selectedSegment = selectedSegment;
		}

		public void setPassWholeRoute(boolean passWholeRoute) {
			this.passWholeRoute = passWholeRoute;
		}

		public boolean isPassWholeRoute() {
			return passWholeRoute;
		}

		public GPXRouteParams build(OsmandApplication app) {
			GPXRouteParams res = new GPXRouteParams();
			try {
				res.prepareGPXFile(this);
			} catch (RuntimeException e) {
				log.error(e.getMessage(), e);
				app.showShortToastMessage(app.getString(R.string.gpx_parse_error) + " " + e.getMessage());
			}
			return res;
		}

		public void setReverse(boolean reverse) {
			this.reverse = reverse;
		}

		public GPXFile getFile() {
			return file;
		}

		public List<Location> getPoints(OsmandApplication app) {
			GPXRouteParams copy = build(app);
			return copy.getPoints();
		}
	}
}