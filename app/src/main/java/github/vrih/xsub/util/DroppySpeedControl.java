package github.vrih.xsub.util;

import android.content.Context;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import com.shehabic.droppy.DroppyClickCallbackInterface;
import com.shehabic.droppy.DroppyMenuCustomItem;

/**
 * Created by marcus on 2/14/2017.
 */
public class DroppySpeedControl extends DroppyMenuCustomItem {

    private SeekBar seekBar;

    public DroppySpeedControl(int customResourceId) {
        super(customResourceId);

    }

    @Override
    public View render(Context context) {
        return super.render(context);


    }

    public void setOnClicks(Context context, final DroppyClickCallbackInterface callback, int ... elementsByID){
        render(context);
        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                callback.call(v, v.getId());
            }
        };
        for (Integer element : elementsByID) {
            renderedView.findViewById(element).setOnClickListener(listener);
        }
    }


    public void updateSeekBar(float playbackSpeed){
        TextView tv = (TextView)seekBar.getTag();
        tv.setText(Float.toString(playbackSpeed));
        seekBar.setProgress((int)(playbackSpeed*10)-5);
    }

    public void setOnSeekBarChangeListener(Context context, final DroppyClickCallbackInterface callback, int seekBarByID, int textViewByID, float playbackSpeed) {
        render(context);
        final TextView textBox = renderedView.findViewById(textViewByID);
        textBox.setText(Float.toString(playbackSpeed));
        SeekBar seekBar = renderedView.findViewById(seekBarByID);
        this.seekBar = seekBar;
        seekBar.setTag(textBox);
        seekBar.setProgress((int)(playbackSpeed*10)-5);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    textBox.setText(Float.valueOf(String.valueOf((progress + 5) / 10.0)).toString());
                    seekBar.setProgress(progress);
                    callback.call(seekBar,seekBar.getId());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        seekBar.setProgress((int)((playbackSpeed/10.0) - 5));
    }
}
