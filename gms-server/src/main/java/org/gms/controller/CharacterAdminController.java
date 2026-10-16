package org.gms.controller;

import lombok.AllArgsConstructor;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.service.CharacterAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/character/v1")
public class CharacterAdminController {
    private final CharacterAdminService service;
    @GetMapping("/options")
    public ResultBody<Map<String,Object>> options(@RequestParam(value="q",defaultValue="") String q,
        @RequestParam(value="world",required=false) Integer world,@RequestParam(value="id",required=false) Integer id,
        @RequestParam(value="page",defaultValue="1") int page) throws SQLException {return ResultBody.success(service.options(q,world,id,page));}
    @GetMapping("/manage/{cid}")
    public ResultBody<Map<String,Object>> detail(@PathVariable("cid") int cid) throws SQLException {return ResultBody.success(service.detail(cid));}
    @PostMapping("/manage/preview")
    public ResultBody<Map<String,Object>> preview(@RequestBody SubmitBody<CharacterAdminService.Edit> body,Principal principal) throws SQLException {
        return ResultBody.success(service.edit(body.getData(),principal.getName(),false));
    }
    @PostMapping("/manage/save")
    public ResultBody<Map<String,Object>> save(@RequestBody SubmitBody<CharacterAdminService.Edit> body,Principal principal) throws SQLException {
        return ResultBody.success(service.edit(body.getData(),principal.getName(),true));
    }
    @GetMapping("/manage/history/{cid}")
    public ResultBody<List<Map<String,Object>>> history(@PathVariable("cid") int cid) throws SQLException {return ResultBody.success(service.history(cid));}
}
