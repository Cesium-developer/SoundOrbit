package com.example.surroundinsert;

import android.content.Context;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class SurroundTileService extends TileService {

    public static void requestUpdate(Context c) {
        try {
            TileService.requestListeningState(c, new android.content.ComponentName(c, SurroundTileService.class));
        } catch (Throwable ignored) {
        }
    }

    private void refresh() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(SurroundService.isRunning() ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        refresh();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (SurroundService.isRunning()) {
            SurroundService.stop(this);
        } else {
            SurroundService.start(this, Prefs.getDepth(this), Prefs.getPeriod(this), Prefs.getRatio(this));
        }
        refresh();
    }
}
