package ke.ac.strathmore.fmls.controller;

import jakarta.validation.Valid;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.MessageView;
import ke.ac.strathmore.fmls.dto.SendMessageRequest;
import ke.ac.strathmore.fmls.dto.ThreadView;
import ke.ac.strathmore.fmls.security.RequireRole;
import ke.ac.strathmore.fmls.service.MessageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/messages")
@RequireRole({Role.FARMER, Role.BUYER})
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @GetMapping("/threads")
    public List<ThreadView> threads() {
        return messages.threads();
    }

    @GetMapping("/thread/{counterpartId}")
    public List<MessageView> thread(@PathVariable Long counterpartId) {
        return messages.conversation(counterpartId);
    }

    @PostMapping
    public MessageView send(@Valid @RequestBody SendMessageRequest req) {
        return messages.send(req);
    }
}
