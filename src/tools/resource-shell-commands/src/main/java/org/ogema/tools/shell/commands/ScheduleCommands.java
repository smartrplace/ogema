package org.ogema.tools.shell.commands;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.Parameter;
import org.ogema.core.application.Application;
import org.ogema.core.application.ApplicationManager;
import org.ogema.core.model.Resource;
import org.ogema.core.model.schedule.Schedule;
import org.ogema.core.model.simple.BooleanResource;
import org.ogema.core.model.simple.FloatResource;
import org.ogema.core.model.simple.IntegerResource;
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
				"osgi.command.function=size",
		}
)
public class ScheduleCommands {
	
	private static final String[] FORMATS = {"yyyy-MM-ddTHH:mm:ss","yyyy-MM-ddTHH:mm:ssZ", "yyyy-MM-ddTHH:mm", "yyyy-MM-ddTHH", "yyyy-MM-dd", "yyyy-MM-ddTHH:mmZ", "yyyy-MM-ddTHHZ", "yyyy-MM-ddZ"};
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
			@Descriptor("Schedule resource path")
			String path
			) throws InterruptedException {
		if (path.trim().isEmpty())
			return 0;
		startLatch.await(30, TimeUnit.SECONDS);
		final ReadOnlyTimeSeries r = ScheduleCommands.getTimeseries(appMan.getResourceAccess().getResource(path));
		return size(startTime, endTime, r);
	}
	
	private static ReadOnlyTimeSeries getTimeseries(Resource r) {
		if (r == null)
			return null;
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
		return null;
	}
	
	private static Long parseDatetime(String datetime) {
		if (datetime == null || datetime.isEmpty())
			return null;
		try {
			return Long.parseLong(datetime);
		} catch (NumberFormatException e) {}
		for (String format: FORMATS) {
			try {
				new SimpleDateFormat(format).parse(datetime).getTime();
			} catch (ParseException e) {}
		}
		return null;
	}
	

}
