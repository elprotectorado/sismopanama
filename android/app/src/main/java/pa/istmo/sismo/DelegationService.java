package pa.istmo.sismo;

import com.google.androidbrowserhelper.locationdelegation.LocationDelegationExtraCommandHandler;

/** Permite que la web use las notificaciones y la ubicación del sistema a través de la app. */
public class DelegationService extends com.google.androidbrowserhelper.trusted.DelegationService {
    @Override
    public void onCreate() {
        super.onCreate();
        registerExtraCommandHandler(new LocationDelegationExtraCommandHandler());
    }
}
