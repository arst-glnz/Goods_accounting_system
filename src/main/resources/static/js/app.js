document.addEventListener("DOMContentLoaded", () => {
    const catalogFilter = document.querySelector("#catalog-filter");
    const search = document.querySelector("#product-search");
    const stockFilter = document.querySelector("#stock-filter");
    const pageSize = document.querySelector("#page-size");
    let searchTimer;
    search?.addEventListener("input", () => {
        window.clearTimeout(searchTimer);
        searchTimer = window.setTimeout(() => catalogFilter?.requestSubmit(), 350);
    });
    stockFilter?.addEventListener("change", () => catalogFilter?.requestSubmit());
    pageSize?.addEventListener("change", () => catalogFilter?.requestSubmit());

    document.querySelectorAll(".delete-form").forEach((form) => {
        form.addEventListener("submit", (event) => {
            const name = form.dataset.productName;
            if (!window.confirm(`Удалить товар «${name}»? Это действие нельзя отменить.`)) {
                event.preventDefault();
            }
        });
    });

    const orderLines = document.querySelector("#order-lines");
    const addOrderLine = document.querySelector("#add-order-line");
    const orderLineTemplate = document.querySelector("#order-line-template");
    addOrderLine?.addEventListener("click", () => {
        if (!orderLines || !orderLineTemplate) return;
        const index = orderLines.querySelectorAll(".order-line").length;
        const wrapper = document.createElement("div");
        wrapper.innerHTML = orderLineTemplate.innerHTML.replaceAll("__INDEX__", String(index));
        const line = wrapper.firstElementChild;
        if (line) orderLines.append(line);
    });
    orderLines?.addEventListener("click", (event) => {
        const target = event.target;
        if (!(target instanceof Element)) return;
        const removeButton = target.closest(".remove-order-line");
        if (!removeButton) return;
        const lines = orderLines.querySelectorAll(".order-line");
        const currentLine = removeButton.closest(".order-line");
        if (lines.length > 1) {
            currentLine?.remove();
        } else if (currentLine) {
            currentLine.querySelectorAll("select, input").forEach((field) => {
                field.value = "";
            });
        }
    });

    const faqPanel = document.querySelector("#faq-panel");
    const faqOpen = document.querySelector("#faq-open");
    const faqClose = document.querySelector("#faq-close");
    const faqForm = document.querySelector("#faq-form");
    const faqInput = document.querySelector("#faq-question");
    const faqMessages = document.querySelector("#faq-messages");
    const faqStatus = document.querySelector("#faq-status");
    const faqSubmit = faqForm?.querySelector("button[type='submit']");
    let faqRequestPending = false;

    function setFaqOpen(open) {
        if (!faqPanel || !faqOpen) return;
        faqPanel.hidden = !open;
        faqOpen.setAttribute("aria-expanded", String(open));
        if (open) faqInput?.focus();
    }

    faqOpen?.addEventListener("click", () => setFaqOpen(faqPanel?.hidden ?? false));
    faqClose?.addEventListener("click", () => setFaqOpen(false));

    function appendMessage(text, className) {
        if (!faqMessages) return null;
        const message = document.createElement("p");
        message.className = `chat-message ${className}`;
        message.textContent = text;
        faqMessages.append(message);
        faqMessages.scrollTop = faqMessages.scrollHeight;
        return message;
    }

    async function askFaq(question) {
        if (faqRequestPending) return;
        const csrfHeader = faqForm?.dataset.csrfHeader;
        const csrfToken = faqForm?.dataset.csrfToken;
        appendMessage(question, "chat-question");
        faqRequestPending = true;
        if (faqSubmit) faqSubmit.disabled = true;
        if (faqStatus) faqStatus.textContent = "Отправляем вопрос…";
        try {
            const response = await fetch("/api/faq", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    [csrfHeader]: csrfToken
                },
                body: JSON.stringify({ question })
            });
            if (!response.ok) throw new Error(`FAQ request failed (${response.status})`);
            const data = await response.json();
            appendMessage(data.answer, "chat-answer");
            if (faqStatus) faqStatus.textContent = "";
        } catch (error) {
            const message = appendMessage("Не удалось получить ответ.", "chat-answer");
            if (message) {
                const retryButton = document.createElement("button");
                retryButton.type = "button";
                retryButton.className = "faq-retry";
                retryButton.textContent = "Повторить";
                retryButton.addEventListener("click", () => {
                    retryButton.remove();
                    askFaq(question);
                }, { once: true });
                message.append(" ", retryButton);
            }
            if (faqStatus) faqStatus.textContent = "Проверьте соединение и попробуйте снова.";
            console.error("FAQ request failed", error);
        } finally {
            faqRequestPending = false;
            if (faqSubmit) faqSubmit.disabled = false;
        }
    }

    faqForm?.addEventListener("submit", (event) => {
        event.preventDefault();
        const question = faqInput?.value.trim();
        if (!question) return;
        faqInput.value = "";
        askFaq(question);
    });

    document.querySelectorAll("[data-question]").forEach((button) => {
        button.addEventListener("click", () => askFaq(button.dataset.question));
    });
});
