package dagger.fragment;

import android.app.ListFragment;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import dagger.adapter.StabbedAdapter;
import dagger.bot.R;

import javax.inject.Inject;

/**
 * @author christopherperry
 */
public class StabbedFragment extends ListFragment {
  @Inject Resources resources;
  @Inject StabbedAdapter adapter;

  @Override public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                     Bundle savedInstanceState) {
    return inflater.inflate(R.layout.stabbed_fragment, null);
  }

  @Override public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);

    String text = resources.getString(R.string.fragmentStab);
    Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();

    getListView().setAdapter(adapter);
  }
}
