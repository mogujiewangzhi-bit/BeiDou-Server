package org.gms.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import org.gms.constants.api.ApiConstant;
import org.gms.model.dto.GiveResourceReqDTO;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.service.GiveService;
import org.gms.service.OfflineGrantService;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/give")
public class GiveController {
    @Autowired
    private final GiveService giveService;
    private final OfflineGrantService offlineGrantService;

    @Tag(name = "/give/" + ApiConstant.LATEST)
    @Operation(summary = "给玩家分发资源")
    @PostMapping("/" + ApiConstant.LATEST + "/resource")
    public ResultBody<Object> giveResource(@RequestBody SubmitBody<GiveResourceReqDTO> submitBody) throws SQLException {
        if (offlineGrantService.queueIfOffline(submitBody.getData(), submitBody.getRequestId())) {
            return ResultBody.success(Map.of("queued", true));
        }
        giveService.give(submitBody.getData());
        return ResultBody.success(Map.of("queued", false));
    }

    @GetMapping("/" + ApiConstant.LATEST + "/rewards/{cid}")
    public ResultBody<List<Map<String, Object>>> rewards(@PathVariable("cid") int cid) throws SQLException {
        return ResultBody.success(offlineGrantService.history(cid));
    }
}
