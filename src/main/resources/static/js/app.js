(function () {
    'use strict';

    function initFindChats() {
        var button = document.getElementById('findChatsBtn');
        var resultBox = document.getElementById('chatsResult');
        if (!button || !resultBox) {
            return;
        }

        button.addEventListener('click', function () {
            var tokenInput = document.querySelector('input[name="telegramBotToken"]');
            var chatInput = document.querySelector('input[name="telegramChatId"]');
            var token = tokenInput ? tokenInput.value.trim() : '';

            button.disabled = true;
            resultBox.hidden = false;
            resultBox.textContent = 'Запрашиваю чаты у Telegram…';

            var body = new URLSearchParams();
            body.set('telegramBotToken', token);

            fetch('/settings/find-chats', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                body: body.toString()
            })
                .then(function (response) {
                    return response.json();
                })
                .then(function (data) {
                    resultBox.textContent = '';
                    var message = document.createElement('div');
                    message.textContent = data.message || '';
                    resultBox.appendChild(message);

                    if (data.ok && data.chats && data.chats.length > 0) {
                        var list = document.createElement('ul');
                        data.chats.forEach(function (chat) {
                            var item = document.createElement('li');
                            var label = document.createElement('span');
                            label.textContent = chat.title + ' — ' + chat.id + ' ';
                            item.appendChild(label);

                            var use = document.createElement('button');
                            use.type = 'button';
                            use.className = 'btn btn--small';
                            use.textContent = 'Использовать';
                            use.addEventListener('click', function () {
                                if (chatInput) {
                                    var existing = chatInput.value.trim();
                                    if (existing && existing.indexOf(String(chat.id)) === -1) {
                                        chatInput.value = existing + ',' + chat.id;
                                    } else if (!existing) {
                                        chatInput.value = chat.id;
                                    }
                                }
                            });
                            item.appendChild(use);
                            list.appendChild(item);
                        });
                        resultBox.appendChild(list);
                    } else if (data.ok) {
                        var hint = document.createElement('div');
                        hint.className = 'muted';
                        hint.textContent = 'Чаты не найдены. Напишите боту /start в Telegram и попробуйте снова.';
                        resultBox.appendChild(hint);
                    }
                })
                .catch(function (error) {
                    resultBox.textContent = 'Ошибка запроса: ' + error;
                })
                .finally(function () {
                    button.disabled = false;
                });
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initFindChats);
    } else {
        initFindChats();
    }
})();
