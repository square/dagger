package dagger.adapter;

import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import dagger.bot.R;

import javax.inject.Inject;

public class StabbedAdapter extends BaseAdapter {
  private LayoutInflater layoutInflater;
  private final String rowText;

  @Inject StabbedAdapter(LayoutInflater layoutInflater, Resources resources) {
    this.layoutInflater = layoutInflater;
    this.rowText = resources.getString(R.string.rowText);
  }

  @Override public View getView(int position, View view, ViewGroup viewGroup) {
    View row = layoutInflater.inflate(R.layout.adapter_view, null);
    ((TextView) row.findViewById(R.id.rowText)).setText(rowText);
    return row;
  }

  @Override public int getCount() {
    return 666;
  }

  @Override public Object getItem(int i) {
    return null;
  }

  @Override public long getItemId(int i) {
    return 0;
  }
}
