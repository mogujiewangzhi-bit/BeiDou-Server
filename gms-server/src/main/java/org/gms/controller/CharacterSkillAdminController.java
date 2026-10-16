package org.gms.controller;

import lombok.AllArgsConstructor;
import org.gms.model.dto.ResultBody;
import org.gms.model.dto.SubmitBody;
import org.gms.service.CharacterSkillAdminService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@AllArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/character/v1/skills")
public class CharacterSkillAdminController {
    private final CharacterSkillAdminService service;
    @GetMapping("/catalog") public ResultBody<Map<String,Object>> catalog(@RequestParam("cid") int cid,
        @RequestParam(value="q",defaultValue="") String q,@RequestParam(value="page",defaultValue="1") int page) throws SQLException {return ResultBody.success(service.catalog(cid,q,page));}
    @GetMapping("/{cid}") public ResultBody<Map<String,Object>> list(@PathVariable("cid") int cid) throws SQLException {return ResultBody.success(service.list(cid));}
    @GetMapping("/history/{cid}") public ResultBody<List<Map<String,Object>>> history(@PathVariable("cid") int cid) throws SQLException {return ResultBody.success(service.history(cid));}
    @PostMapping("/preview") public ResultBody<Map<String,Object>> preview(@RequestBody SubmitBody<CharacterSkillAdminService.Edit> body,Principal actor) throws SQLException {return ResultBody.success(service.edit(body.getData(),actor.getName(),false));}
    @PostMapping("/save") public ResultBody<Map<String,Object>> save(@RequestBody SubmitBody<CharacterSkillAdminService.Edit> body,Principal actor) throws SQLException {return ResultBody.success(service.edit(body.getData(),actor.getName(),true));}
}
