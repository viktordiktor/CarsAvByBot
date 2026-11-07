package com.avby.avby.web;

import com.avby.avby.domain.AppSettings;
import com.avby.avby.service.AppSettingsService;
import com.avby.avby.telegram.TelegramClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private final AppSettingsService settingsService;
    private final TelegramClient telegramClient;

    public SettingsController(AppSettingsService settingsService, TelegramClient telegramClient) {
        this.settingsService = settingsService;
        this.telegramClient = telegramClient;
    }

    @GetMapping
    public String form(Model model) {
        AppSettings settings = settingsService.get();
        model.addAttribute("form", SettingsForm.from(settings));
        model.addAttribute("settings", settings);
        return "settings";
    }

    @PostMapping
    public String save(@ModelAttribute("form") SettingsForm form, RedirectAttributes redirect) {
        settingsService.update(
                form.getTelegramBotToken(),
                form.getTelegramChatId(),
                form.isTelegramEnabled(),
                form.isSendPhoto(),
                form.getPollIntervalMinutes(),
                form.getRequestDelayMs()
        );
        redirect.addFlashAttribute("message", "Настройки сохранены");
        return "redirect:/settings";
    }

    @PostMapping("/test")
    public String test(
            @RequestParam(name = "telegramBotToken", required = false) String token,
            @RequestParam(name = "telegramChatId", required = false) String chatId,
            RedirectAttributes redirect
    ) {
        String effectiveToken = token == null || token.isBlank()
                ? settingsService.get().getTelegramBotToken()
                : token;
        TelegramClient.TelegramResult result = telegramClient.sendTest(effectiveToken, chatId);
        redirect.addFlashAttribute(result.ok() ? "message" : "error", result.message());
        return "redirect:/settings";
    }

    @PostMapping("/find-chats")
    @ResponseBody
    public Map<String, Object> findChats(
            @RequestParam(name = "telegramBotToken", required = false) String token
    ) {
        String effectiveToken = token == null || token.isBlank()
                ? settingsService.get().getTelegramBotToken()
                : token;
        TelegramClient.ChatsResult result = telegramClient.findChats(effectiveToken);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", result.ok());
        response.put("message", result.message());
        response.put("chats", result.chats());
        return response;
    }

    @PostMapping("/clear-token")
    public String clearToken(RedirectAttributes redirect) {
        settingsService.clearTelegramToken();
        redirect.addFlashAttribute("message", "Токен бота удалён");
        return "redirect:/settings";
    }
}
