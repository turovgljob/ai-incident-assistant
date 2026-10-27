package com.example.incidents.api;

import com.example.incidents.agent.IncidentAgent;
import com.example.incidents.model.AnalysisResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentAgent agent;

    public IncidentController(IncidentAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/analyze")
    public AnalysisResult analyze(@Valid @RequestBody AnalyzeRequest request) {
        return agent.analyze(request.description());
    }
}
