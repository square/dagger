package dagger.activity;

import android.app.Activity;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import dagger.application.DaggerApplication;
import dagger.bot.R;
import dagger.fragment.StabbedFragment;

import javax.inject.Inject;

public class StabbedActivity extends Activity {
  @Inject LocationManager locationManager;
  private TextView locationView;
  private Button button;

  @Override protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.stabbed_activity);
    DaggerApplication.inject(this);

    locationView = (TextView) findViewById(R.id.locationView);
    button = (Button) findViewById(R.id.button);

    Location location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
    String text = String.format("Last known location: (%f, %f)",
      location.getLatitude(), location.getLongitude());
    locationView.setText(text);

    button.setOnClickListener(new FragmentStabber());
  }

  private class FragmentStabber implements View.OnClickListener {
    @Override public void onClick(View view) {
      StabbedFragment stabbedFragment = new StabbedFragment();
      DaggerApplication.inject(stabbedFragment);

      getFragmentManager().beginTransaction()
        .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
        .replace(R.id.container, stabbedFragment).addToBackStack(null).commit();

    }
  }
}
