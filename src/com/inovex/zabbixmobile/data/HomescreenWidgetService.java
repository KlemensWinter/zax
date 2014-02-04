package com.inovex.zabbixmobile.data;

import java.util.ArrayList;
import java.util.List;

import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.inovex.zabbixmobile.R;
import com.inovex.zabbixmobile.activities.ProblemsActivity;
import com.inovex.zabbixmobile.exceptions.FatalException;
import com.inovex.zabbixmobile.exceptions.ZabbixLoginRequiredException;
import com.inovex.zabbixmobile.model.HostGroup;
import com.inovex.zabbixmobile.model.Trigger;
import com.inovex.zabbixmobile.model.TriggerSeverity;
import com.inovex.zabbixmobile.model.Cache.CacheDataType;
import com.inovex.zabbixmobile.widget.ZaxWidgetProvider;
import com.j256.ormlite.android.apptools.OpenHelperManager;

/**
 * Started service providing the homescreen widget with functionality to
 * retrieve data from Zabbix (at the moment the active triggers).
 * 
 */
public class HomescreenWidgetService extends Service {
	private enum DisplayStatus {
		ZAX_ERROR(R.drawable.severity_high), OK(R.drawable.ok), AVG(
				R.drawable.severity_average), HIGH(R.drawable.severity_high), LOADING(
				R.drawable.icon);

		private int drawable;

		DisplayStatus(int drawable) {
			this.drawable = drawable;
		}

		int getDrawable() {
			return drawable;
		}
	}

	public static final String UPDATE = "update";
	private static final String TAG = HomescreenWidgetService.class
			.getSimpleName();

	private ZabbixRemoteAPI mRemoteAPI;
	private DatabaseHelper mDatabaseHelper;

	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		Log.d(TAG, "onStart");
		updateView(null, getResources().getString(R.string.widget_loading),
				false);
		if (mDatabaseHelper == null) {
			// set up SQLite connection using OrmLite
			mDatabaseHelper = OpenHelperManager.getHelper(this,
					DatabaseHelper.class);
		}
		if (mRemoteAPI == null) {
			mRemoteAPI = new ZabbixRemoteAPI(this.getApplicationContext(),
					mDatabaseHelper, null, null);
		}

		// authenticate
		RemoteAPITask loginTask = new RemoteAPITask(mRemoteAPI) {

			private List<Trigger> problems;
			private boolean error;

			@Override
			protected void executeTask() throws ZabbixLoginRequiredException,
					FatalException {
				problems = new ArrayList<Trigger>();
				try {
					mRemoteAPI.authenticate();
					// we need to refresh triggers AND events because otherwise
					// we might lose triggers belonging to events
					mDatabaseHelper.clearEvents();
					mDatabaseHelper.clearTriggers();
					mDatabaseHelper.setNotCached(CacheDataType.EVENT, null);
					mDatabaseHelper.setNotCached(CacheDataType.TRIGGER, null);
					mRemoteAPI.importActiveTriggers(null);
					mRemoteAPI.importEvents(null);
				} catch (FatalException e) {
					error = true;
					return;
				} finally {
					problems = mDatabaseHelper
							.getProblemsBySeverityAndHostGroupId(
									TriggerSeverity.ALL, HostGroup.GROUP_ID_ALL);
				}
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				if (error) {
					updateView(
							DisplayStatus.ZAX_ERROR.getDrawable(),
							getResources().getString(
									R.string.widget_connection_error), false);
					stopSelf();
					return;
				}
				if (problems != null) {
					// for (Trigger t : problems)
					// Log.d(TAG, t.toString());
					String status;
					int icon = R.drawable.ok;
					if (problems.size() > 0) {
						int countHigh = 0;
						int countAverage = 0;
						for (Trigger trigger : problems) {
							// make sure disabled triggers are not ignored
							if (trigger.getStatus() != Trigger.STATUS_ENABLED)
								continue;
							if (trigger.getPriority() == TriggerSeverity.DISASTER
									|| trigger.getPriority() == TriggerSeverity.HIGH)
								countHigh++;
							else
								countAverage++;
						}
						if (countHigh > 0)
							icon = DisplayStatus.HIGH.getDrawable();
						else if (countAverage > 0)
							icon = DisplayStatus.AVG.getDrawable();
						else
							icon = DisplayStatus.OK.getDrawable();
						status = problems.get(0).getDescription();
						status = getResources().getString(
								R.string.widget_problems, countHigh,
								countAverage);
					} else {
						icon = DisplayStatus.OK.getDrawable();
						status = getResources().getString(R.string.ok);

					}
					updateView(icon, status, false);
					stopSelf();
				}
			}

		};
		loginTask.execute();
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}

	private void updateView(Integer statusIcon, String statusText,
			boolean startProgressSpinner) {
		AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(getApplicationContext());
		ComponentName thisWidget = new ComponentName(getApplicationContext(),
				ZaxWidgetProvider.class);
		int[] allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget);

		for (int widgetId : allWidgetIds) {

			RemoteViews remoteViews = new RemoteViews(getApplicationContext()
					.getPackageName(), R.layout.homescreen_widget_small);

			// ZaxPreferences preferences = ZaxPreferences.getInstance(this);
			// remoteViews.setTextViewText(R.id.widget_headline,
			// preferences.getZabbixUrl());

			if (startProgressSpinner) {
				remoteViews.setViewVisibility(R.id.refresh_button, View.GONE);
				remoteViews.setViewVisibility(R.id.refresh_progress,
						View.VISIBLE);
			} else {
				remoteViews
						.setViewVisibility(R.id.refresh_button, View.VISIBLE);
				remoteViews.setViewVisibility(R.id.refresh_progress, View.GONE);
			}

			// Set the text
			remoteViews.setTextViewText(R.id.content, statusText);

			// set icon
			if (statusIcon != null)
				remoteViews
						.setImageViewResource(R.id.status_button, statusIcon);

			// int moreProblems = problems.size() - 1;
			//
			// if (moreProblems == 1)
			// remoteViews.setTextViewText(R.id.widget_more, getResources()
			// .getString(R.string.widget_more_problem, moreProblems));
			// else
			// remoteViews
			// .setTextViewText(
			// R.id.widget_more,
			// getResources().getString(
			// R.string.widget_more_problems,
			// moreProblems));

			// status button click
			Intent statusButtonClickIntent = new Intent(
					this.getApplicationContext(), ProblemsActivity.class);
			statusButtonClickIntent.setAction(Intent.ACTION_MAIN);
			statusButtonClickIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			PendingIntent pendingIntent = PendingIntent.getActivity(
					getApplicationContext(), 0, statusButtonClickIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.widget, pendingIntent);

			// refresh click
			Intent refreshClickIntent = new Intent(
					this.getApplicationContext(), ZaxWidgetProvider.class);
			refreshClickIntent
					.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
			refreshClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS,
					allWidgetIds);

			PendingIntent pendingIntent2 = PendingIntent.getBroadcast(
					getApplicationContext(), 1, refreshClickIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
			remoteViews.setOnClickPendingIntent(R.id.refresh_button,
					pendingIntent2);
			appWidgetManager.updateAppWidget(widgetId, remoteViews);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

}
