package ke.ac.strathmore.fmls.service;

import ke.ac.strathmore.fmls.domain.Buyer;
import ke.ac.strathmore.fmls.domain.Farmer;
import ke.ac.strathmore.fmls.domain.Message;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.Role;
import ke.ac.strathmore.fmls.dto.MessageView;
import ke.ac.strathmore.fmls.dto.SendMessageRequest;
import ke.ac.strathmore.fmls.dto.ThreadView;
import ke.ac.strathmore.fmls.repository.BuyerRepository;
import ke.ac.strathmore.fmls.repository.FarmerRepository;
import ke.ac.strathmore.fmls.repository.MessageRepository;
import ke.ac.strathmore.fmls.repository.ProductRepository;
import ke.ac.strathmore.fmls.security.AuthContext;
import ke.ac.strathmore.fmls.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Direct buyer ↔ farmer communication ("Contact Seller"). */
@Service
public class MessageService {

    private final MessageRepository messages;
    private final FarmerRepository farmers;
    private final BuyerRepository buyers;
    private final ProductRepository products;
    private final ActivityLogService activity;

    public MessageService(MessageRepository messages, FarmerRepository farmers, BuyerRepository buyers,
                          ProductRepository products, ActivityLogService activity) {
        this.messages = messages;
        this.farmers = farmers;
        this.buyers = buyers;
        this.products = products;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<ThreadView> threads() {
        CurrentUser me = AuthContext.get();
        List<Message> all = me.role() == Role.FARMER ? messages.forFarmer(me.id())
                : me.role() == Role.BUYER ? messages.forBuyer(me.id()) : List.of();
        Map<Long, List<Message>> byCounterpart = new LinkedHashMap<>();
        for (Message m : all) {
            Long other = me.role() == Role.FARMER ? m.getBuyer().getId() : m.getFarmer().getId();
            byCounterpart.computeIfAbsent(other, k -> new ArrayList<>()).add(m);
        }
        List<ThreadView> out = new ArrayList<>();
        byCounterpart.forEach((other, list) -> {
            Message last = list.get(0);
            long unread = list.stream().filter(m -> !m.isRead() && m.getSenderRole() != me.role()).count();
            boolean farmerSide = me.role() == Role.FARMER;
            String name = farmerSide ? last.getBuyer().getName() : last.getFarmer().getName();
            String loc = farmerSide ? last.getBuyer().getLocation() : last.getFarmer().getLocation();
            String phone = farmerSide ? last.getBuyer().getPhone() : last.getFarmer().getPhone();
            out.add(new ThreadView(other, name, loc, phone, last.getContent(), last.getCreatedAt(), unread));
        });
        return out;
    }

    @Transactional
    public List<MessageView> conversation(Long counterpartId) {
        CurrentUser me = AuthContext.get();
        List<Message> list = me.role() == Role.FARMER ? messages.conversation(me.id(), counterpartId)
                : messages.conversation(counterpartId, me.id());
        for (Message m : list) {
            if (!m.isRead() && m.getSenderRole() != me.role()) {
                m.setRead(true);
                messages.save(m);
            }
        }
        return list.stream().map(m -> ViewMapper.message(m, me)).collect(Collectors.toList());
    }

    @Transactional
    public MessageView send(SendMessageRequest req) {
        CurrentUser me = AuthContext.get();
        Farmer farmer;
        Buyer buyer;
        if (me.role() == Role.FARMER) {
            farmer = farmers.findById(me.id()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
            buyer = buyers.findById(req.counterpartId()).orElseThrow(() -> ApiException.notFound("Buyer not found"));
        } else if (me.role() == Role.BUYER) {
            buyer = buyers.findById(me.id()).orElseThrow(() -> ApiException.unauthorized("Account not found"));
            farmer = farmers.findById(req.counterpartId()).orElseThrow(() -> ApiException.notFound("Farmer not found"));
        } else {
            throw ApiException.forbidden("Only farmers and buyers can send messages");
        }
        Message m = new Message();
        m.setFarmer(farmer);
        m.setBuyer(buyer);
        m.setSenderRole(me.role());
        m.setContent(req.content().trim());
        if (req.productId() != null) {
            Product p = products.findById(req.productId()).orElse(null);
            if (p != null && p.getFarmer().getId().equals(farmer.getId())) m.setProduct(p);
        }
        m = messages.save(m);
        activity.log("MESSAGE_SENT", me.name() + " -> " + (me.role() == Role.FARMER ? buyer.getName() : farmer.getName()));
        return ViewMapper.message(m, me);
    }
}
