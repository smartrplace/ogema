package org.ogema.tools.shell.commands;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.channelmanager.measurements.SampledValue;
import org.ogema.core.model.Resource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
import org.ogema.core.model.simple.SingleValueResource;
import org.ogema.core.model.simple.TimeResource;
import org.ogema.core.timeseries.ReadOnlyTimeSeries;
import org.osgi.framework.BundleContext;	
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;

@Component(
		service=ScheduleCommands.class,
		property= {
				"osgi.command.scope=schedule",

				"osgi.command.function=getValues",
				"osgi.command.function=printValues",
				"osgi.command.function=size",
		}
)
public class ScheduleCommands {
	
	private static final String[] PARSE_FORMATS = {"yyyy-MM-dd'T'HH:mm:ss","yyyy-MM-dd'T'HH:mm:ssZ", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd'T'HH", "yyyy-MM-dd", "yyyy-MM-dd'T'HH:mmZ", "yyyy-MM-dd'T'HHZ", "yyyy-MM-ddZ"};
	private static final String PRINT_FORMAT = "yyyy-MM-dd'T'HH:mm:ssZ";
	private final CountDownLatch startLatch = new CountDownLatch(1);
	private volatile ServiceRegistration<Application> appService;
	private ApplicationManager appMan;
	

	@Activate
	protected void activate(BundleContext ctx) {
		final Application app = new Application() {

			@Override
			public void start(ApplicationManager appManager) {
				appMan = appManager;
				startLatch.countDown();
			}

			@Override
			public void stop(AppStopReason reason) {
				appMan = null;
			}
			
		};
		this.appService = ctx.registerService(Application.class, app, null);
	}
	
	@Deactivate 
	protected void deactivate() {
		final ServiceRegistration<Application> appService = this.appService;
		if (appService != null) {
			try {
				appService.unregister();
			} catch (Exception ignore) {}
			this.appService = null;
			this.appMan = null;
		} 
				
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		Long start = ScheduleCommands.parseDatetime(startTime);
		Long end = ScheduleCommands.parseDatetime(endTime);
		if (start == null)
			start = Long.MIN_VALUE;
		if (end == null)
			end = Long.MAX_VALUE;
		List<SampledValue> values = schedule.getValues(start, end);
		if (limit >= 0 && values.size() > limit) {
			final List<SampledValue> newValues = new ArrayList<>(limit);
			final int startIdx = fromBeginning ? 0 : values.size() - limit;
			final int endIdx = startIdx + limit;
			for (int idx=startIdx; idx<endIdx; idx++) {
				newValues.add(values.get(idx));
			}
			values = newValues;
		}
		return values;
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return getValues(startTime, endTime, limit, fromBeginning, schedule);
	}
	
	@Descriptor("Get data points from a timeseries")
	public List<SampledValue> getValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return getValues(startTime, endTime, limit, fromBeginning, r);
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		final List<SampledValue> values = getValues(startTime, endTime, limit, fromBeginning, schedule);
		if (values== null)
			return null;
		final Map<String, SampledValue> map = new LinkedHashMap<>();
		final SimpleDateFormat format = new SimpleDateFormat(PRINT_FORMAT);
		for (SampledValue sv: values) {
			map.put(format.format(new Date(sv.getTimestamp())), sv);
		}
		return map;
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return null;
		return printValues(startTime, endTime, limit, fromBeginning, schedule);
	}
	
	@Descriptor("Print data points from a timeseries")
	public Map<String, SampledValue> printValues(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Optional limit")
			@Parameter(names= { "-l", "--limit"}, absentValue = "-1")
			int limit,
			@Descriptor("Optional flag indicating whether the first or last values are to be returned, in case a limit is set.")
			@Parameter(names= { "-fb", "--from-beginning"}, absentValue = "false", presentValue="true")
			boolean fromBeginning,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return null;
		return printValues(startTime, endTime, limit, fromBeginning, r);
	}
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Timeseries/Schedule")
			ReadOnlyTimeSeries schedule
			) throws InterruptedException {
		Long start = ScheduleCommands.parseDatetime(startTime);
		Long end = ScheduleCommands.parseDatetime(endTime);
		if (start == null && end == null)
			return schedule.size();
		if (start == null)
			start = Long.MIN_VALUE;
		if (end == null)
			end = Long.MAX_VALUE;
		return schedule.size(start, end);
	}
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("A logged value resource")
			SingleValueResource valueResource
			) throws InterruptedException {
		final ReadOnlyTimeSeries schedule = getTimeseries(valueResource);
		if (schedule == null)
			return 0;
		return size(startTime, endTime, schedule);
	}
	
	@Descriptor("Count the number of data points in a timeseries")
	public int size(
			@Descriptor("Optional start time")
			@Parameter(names= { "-s", "--start"}, absentValue = "")
			String startTime,
			@Descriptor("Optional end time")
			@Parameter(names= { "-e", "--end"}, absentValue = "")
			String endTime,
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		if (path.trim().isEmpty())
			return 0;
		final ReadOnlyTimeSeries r = getTimeseries(path);
		if (r == null)
			return 0;
		return size(startTime, endTime, r);
	}
	
	private ReadOnlyTimeSeries getTimeseries(String path) throws InterruptedException {
		startLatch.await(30, TimeUnit.SECONDS);
		final Resource r = appMan.getResourceAccess().getResource(path);
		if (r == null) {
			System.out.println("Resource " + path + " not found");
			return null;
		}
		return getTimeseries(r);
	}
	
	private static ReadOnlyTimeSeries getTimeseries(Resource r) {
		if (r instanceof Schedule)
			return (Schedule) r;
		if (r instanceof FloatResource)
			return ((FloatResource) r).getHistoricalData();
		if (r instanceof IntegerResource)
			return ((IntegerResource) r).getHistoricalData();
		if (r instanceof TimeResource)
			return ((TimeResource) r).getHistoricalData();
		if (r instanceof BooleanResource)
			return ((BooleanResource) r).getHistoricalData();
		System.out.println("Cannot convert resource " + r + " into a timeseries");
		return null;
	}

	private static Long parseDatetime(String datetime) {
		if (datetime == null || datetime.isEmpty())
			return null;
		try {
			return Long.parseLong(datetime);
		} catch (NumberFormatException e) {}
		for (String format: PARSE_FORMATS) {
			try {
				new SimpleDateFormat(format).parse(datetime).getTime();
			} catch (ParseException e) {}
		}
		return null;
	}
	

}
