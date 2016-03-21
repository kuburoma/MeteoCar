package cz.meteocar.unit.engine.network.task;

import android.util.Log;

import com.google.common.base.Converter;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;

import cz.meteocar.unit.engine.ServiceManager;
import cz.meteocar.unit.engine.event.ErrorViewType;
import cz.meteocar.unit.engine.event.NetworkErrorEvent;
import cz.meteocar.unit.engine.log.AppLog;
import cz.meteocar.unit.engine.network.ErrorCodes;
import cz.meteocar.unit.engine.network.NetworkException;
import cz.meteocar.unit.engine.network.dto.CreateOBDPidRequest;
import cz.meteocar.unit.engine.network.dto.GetOBDPidResponse;
import cz.meteocar.unit.engine.network.dto.OBDPidDto;
import cz.meteocar.unit.engine.network.task.converter.OBDPidEntity2DtoConverter;
import cz.meteocar.unit.engine.storage.helper.ObdPidHelper;
import cz.meteocar.unit.engine.storage.model.ObdPidEntity;

/**
 * Created by Nell on 20.3.2016.
 */
public class OBDPidsTask extends TimerTask {

    private NetworkConnector<Void, GetOBDPidResponse> getConnector = new NetworkConnector<>(Void.class, GetOBDPidResponse.class, "obdPids");
    private NetworkConnector<CreateOBDPidRequest, Void> postConnector = new NetworkConnector<>(CreateOBDPidRequest.class, Void.class, "obdPids");

    private ObdPidHelper dao = ServiceManager.getInstance().db.getObdPidHelper();

    private static final Converter<ObdPidEntity, OBDPidDto> converterForward = new OBDPidEntity2DtoConverter();
    private static final Converter<OBDPidDto, ObdPidEntity> converterBackward = converterForward.reverse();

    @Override
    public void run() {
        if (isNetworkReady()) {
            try {
                List<ObdPidEntity> all = dao.getAll();
                Long updateTime = getLatestUpdateTime(all);

                List<QueryParameter> params = new ArrayList<>();
                params.add(new QueryParameter("lastUpdateTime", String.valueOf(updateTime)));
                GetOBDPidResponse response = getConnector.get(null, params);
                if (response.getRecords().size() == 0) {
                    return;
                }
                dao.deleteAll();
                dao.saveAll(Lists.newArrayList(converterBackward.convertAll(response.getRecords())));
            } catch (NetworkException e) {
                if (ErrorCodes.RECORDS_UPDATE_REQUIRED.toString().equals(e.getErrorResponse().getCode())) {
                    try {
                        postConnector.post(new CreateOBDPidRequest(Lists.newArrayList(converterForward.convertAll(dao.getAll()))));
                    } catch (NetworkException e1) {
                        ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
                    }
                }
                ServiceManager.getInstance().eventBus.post(new NetworkErrorEvent(e.getErrorResponse(), ErrorViewType.DASHBOARD)).asynchronously();
            }
        } else {
            Log.d(AppLog.LOG_TAG_NETWORK, "OBDPidTask: network is offline");
        }

    }

    protected boolean isNetworkReady() {
        return ServiceManager.getInstance().network.isOnline();
    }

    protected Long getLatestUpdateTime(List<ObdPidEntity> OBDPidEntities) {
        Long max = 0L;
        for (ObdPidEntity entity : OBDPidEntities) {
            if (max < entity.getUpdateTime()) {
                max = entity.getUpdateTime();
            }
        }
        return max;
    }
}

