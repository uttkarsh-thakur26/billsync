package com.uttkarsh.billsync.web;

import com.uttkarsh.billsync.dto.AddMemberRequest;
import com.uttkarsh.billsync.dto.BalancesResponse;
import com.uttkarsh.billsync.dto.CreateGroupRequest;
import com.uttkarsh.billsync.dto.GroupResponse;
import com.uttkarsh.billsync.service.BalanceService;
import com.uttkarsh.billsync.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;
    private final BalanceService balanceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a group")
    public GroupResponse create(@Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(request);
    }

    @GetMapping
    @Operation(summary = "List groups")
    public List<GroupResponse> list() {
        return groupService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Group detail with members")
    public GroupResponse get(@PathVariable Long id) {
        return groupService.get(id);
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add a member")
    public GroupResponse addMember(@PathVariable Long id, @Valid @RequestBody AddMemberRequest request) {
        return groupService.addMember(id, request.userId());
    }

    @GetMapping("/{id}/balances")
    @Operation(summary = "Net balance per member")
    public BalancesResponse balances(@PathVariable Long id) {
        return BalancesResponse.from(id, balanceService.balancesFor(id));
    }
}
