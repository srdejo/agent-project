package co.com.srdejo.agentproject.projects.web;

import co.com.srdejo.agentproject.projects.service.ProjectQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectQueryService queryService;

    public ProjectController(ProjectQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ProjectListResponse list() {
        return queryService.listAll();
    }

    @GetMapping("/{id}")
    public ProjectDetailResponse getById(@PathVariable("id") String id) {
        return queryService.getById(id);
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ProjectNotFoundException e) {
        return e.getMessage();
    }
}
