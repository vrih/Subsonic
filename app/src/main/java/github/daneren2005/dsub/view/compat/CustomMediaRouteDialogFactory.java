package github.daneren2005.dsub.view.compat;

import androidx.appcompat.app.MediaRouteChooserDialogFragment;
import androidx.appcompat.app.MediaRouteControllerDialogFragment;
import androidx.appcompat.app.MediaRouteDialogFactory;

public class CustomMediaRouteDialogFactory extends MediaRouteDialogFactory {
	@Override
	public MediaRouteChooserDialogFragment onCreateChooserDialogFragment() {
		return new CustomMediaRouteChooserDialogFragment();
	}

	@Override
	public MediaRouteControllerDialogFragment onCreateControllerDialogFragment() {
		return new CustomMediaRouteControllerDialogFragment();
	}
}
