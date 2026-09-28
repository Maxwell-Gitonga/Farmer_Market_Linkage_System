package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.domain.SystemLog;
import ke.ac.strathmore.fmls.domain.User;
import ke.ac.strathmore.fmls.repository.SystemLogRepository;
import ke.ac.strathmore.fmls.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records system activity for the administrator's "Monitor system activity" use case. */
@Service
public class ActivityLogService {

    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);
    private final SystemLogRepository repo;

    public ActivityLogService(SystemLogRepository repo) {
        this.repo = repo;
    }

    /** Log an action performed by the currently authenticated user (or the system). Runs in its own
     * transaction so a failed log write can never roll back the business operation. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String action, String details) {
        var current = AuthContext.optional();
        write(current.map(c -> c.role()).orElse(null), current.map(c -> c.id()).orElse(null),
                current.map(c -> c.name()).orElse("System"), action, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User actor, String action, String details) {
        write(actor.getRole(), actor.getId(), actor.getName(), action, details);
    }

    private void write(Role role, Long id, String name, String action, String details) {
        try {
            SystemLog l = new SystemLog();
            l.setActorRole(role);
            l.setActorId(id);
            l.setActorName(name);
            l.setAction(action);
            l.setDetails(details == null ? null : details.length() > 500 ? details.substring(0, 497) + "..." : details);
            repo.save(l);
        } catch (Exception e) {
            log.warn("Could not write activity log {}: {}", action, e.getMessage());
        }
    }
}
