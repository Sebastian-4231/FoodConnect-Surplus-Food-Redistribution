document.addEventListener("DOMContentLoaded", function () {
    loadAnalytics();

    document.getElementById("filterForm").addEventListener("submit", function (e) {
        e.preventDefault();
        const start = document.getElementById("startDate").value;
        const end = document.getElementById("endDate").value;
        const food = document.getElementById("foodFilter").value;
        loadAnalytics(start, end, food);
    });

    document.getElementById("btnReset").addEventListener("click", function () {
        document.getElementById("startDate").value = "";
        document.getElementById("endDate").value = "";
        document.getElementById("foodFilter").value = "";
        loadAnalytics();
    });

    document.getElementById("outcomeSelect").addEventListener("change", function () {
        const wasteGroup = document.getElementById("wasteQtyGroup");
        if (this.value === "Discarded / Wasted") {
            wasteGroup.style.display = "block";
        } else {
            wasteGroup.style.display = "none";
        }
    });

    document.getElementById("outcomeForm").addEventListener("submit", submitOutcomeForm);
});

function loadAnalytics(startDate, endDate, foodFilter) {
    let url = "smart-surplus";
    const params = new URLSearchParams();
    if (startDate) params.append("startDate", startDate);
    if (endDate) params.append("endDate", endDate);
    if (foodFilter) params.append("foodFilter", foodFilter);

    if (params.toString()) {
        url += "?" + params.toString();
    }

    fetch(url)
        .then(response => {
            if (response.status === 401) {
                window.location.href = "login.html";
                return;
            }
            if (!response.ok) {
                throw new Error("Failed to load analytics: " + response.statusText);
            }
            return response.json();
        })
        .then(data => {
            if (!data) return;
            renderMetrics(data.metrics);
            renderRecommendations(data.recommendations);
            renderTrends(data.trends);
            renderTopFoods(data.topFoods);
            renderHistory(data.history);
        })
        .catch(err => {
            console.error("Error loading analytics:", err);
        });
}

function renderMetrics(m) {
    if (!m) return;
    document.getElementById("metricListed").textContent = (m.totalListed || 0) + " units";
    document.getElementById("metricDelivered").textContent = (m.totalDelivered || 0) + " units";
    document.getElementById("metricRemaining").textContent = (m.totalRemaining || 0) + " units";
    document.getElementById("metricWasted").textContent = (m.totalWasted || 0) + " units";
    document.getElementById("metricRate").textContent = (m.redistributionRate !== undefined ? m.redistributionRate : "0.0") + "%";
}

function renderRecommendations(recs) {
    const container = document.getElementById("recommendationsList");
    container.innerHTML = "";

    if (!recs || recs.length === 0) {
        container.innerHTML = `
            <div class="rec-card" style="grid-column: 1 / -1; text-align: center; color: var(--muted-text);">
                <p>No food listings recorded yet to analyze. Add food items to begin receiving smart cooking recommendations!</p>
            </div>
        `;
        return;
    }

    recs.forEach(r => {
        const card = document.createElement("div");
        card.className = "rec-card";
        card.innerHTML = `
            <div>
                <div class="rec-card-header">
                    <span style="font-weight:700; color:var(--forest-green); font-size:15px;">${escapeHtml(r.foodName)}</span>
                    <span class="rec-badge ${r.badgeClass}">${escapeHtml(r.badge)}</span>
                </div>
                <div class="rec-title">${escapeHtml(r.title)}</div>
                <div class="rec-body">${escapeHtml(r.recommendation)}</div>
            </div>
            <div>
                <div class="rec-tip">💡 <strong>Smart Tip:</strong> ${escapeHtml(r.actionableTip)}</div>
                <div class="rec-calc"><strong>Transparent Calculation:</strong> ${escapeHtml(r.calculationDetails)}</div>
            </div>
        `;
        container.appendChild(card);
    });
}

function renderTrends(trends) {
    const container = document.getElementById("trendsContainer");
    if (!trends || trends.length === 0) {
        container.innerHTML = "<p style='color:var(--muted-text);'>No trend data recorded yet.</p>";
        return;
    }

    let html = `
        <table style="width:100%; border-collapse:collapse; font-size:13px;">
            <thead>
                <tr style="border-bottom:1.5px solid var(--card-border); color:var(--forest-green); text-align:left;">
                    <th style="padding:8px 6px;">Week</th>
                    <th style="padding:8px 6px;">Listed</th>
                    <th style="padding:8px 6px;">Delivered</th>
                    <th style="padding:8px 6px;">Surplus</th>
                    <th style="padding:8px 6px;">Surplus %</th>
                </tr>
            </thead>
            <tbody>
    `;

    trends.forEach(t => {
        const rate = t.listedQty > 0 ? Math.round((t.surplusQty / t.listedQty) * 100) : 0;
        html += `
            <tr style="border-bottom:1px solid var(--card-border);">
                <td style="padding:8px 6px; font-weight:600;">${escapeHtml(t.weekLabel)}</td>
                <td style="padding:8px 6px;">${t.listedQty}</td>
                <td style="padding:8px 6px; color:#15803d; font-weight:700;">${t.deliveredQty}</td>
                <td style="padding:8px 6px; color:var(--coral-orange); font-weight:700;">${t.surplusQty}</td>
                <td style="padding:8px 6px;">
                    <div style="background:#e5e7eb; border-radius:10px; height:8px; width:60px; display:inline-block; margin-right:6px; overflow:hidden;">
                        <div style="background:var(--coral-orange); height:100%; width:${Math.min(100, rate)}%;"></div>
                    </div>
                    <span>${rate}%</span>
                </td>
            </tr>
        `;
    });

    html += "</tbody></table>";
    container.innerHTML = html;
}

function renderTopFoods(foods) {
    const container = document.getElementById("topFoodsContainer");
    if (!foods || foods.length === 0) {
        container.innerHTML = "<p style='color:var(--muted-text);'>No surplus records found.</p>";
        return;
    }

    let html = `
        <ul style="list-style:none; padding:0; margin:0; display:flex; flex-direction:column; gap:12px;">
    `;

    foods.forEach(f => {
        html += `
            <li style="border-bottom:1px solid var(--card-border); padding-bottom:10px;">
                <div style="display:flex; justify-content:space-between; margin-bottom:4px;">
                    <span style="font-weight:700; color:var(--forest-green); font-size:14px;">${escapeHtml(f.foodName)}</span>
                    <span style="font-weight:800; color:var(--coral-orange); font-size:13px;">${f.surplusQty} ${escapeHtml(f.unit)} surplus</span>
                </div>
                <div style="font-size:12px; color:var(--muted-text); display:flex; justify-content:space-between;">
                    <span>Listed: ${f.listedQty} ${escapeHtml(f.unit)}</span>
                    <span>Delivered: ${f.deliveredQty} ${escapeHtml(f.unit)}</span>
                </div>
            </li>
        `;
    });

    html += "</ul>";
    container.innerHTML = html;
}

function renderHistory(history) {
    const tbody = document.getElementById("historyTableBody");
    tbody.innerHTML = "";

    if (!history || history.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="9" style="text-align:center; padding:30px; color:var(--muted-text);">
                    No historical food records match the specified filters.
                </td>
            </tr>
        `;
        return;
    }

    history.forEach(item => {
        const tr = document.createElement("tr");

        let statusClass = "tag-available";
        if (item.status === "Requested") statusClass = "tag-requested";
        if (item.status === "Closed" || item.status === "Removed") statusClass = "tag-closed";

        let outcomeClass = "tag-available";
        if (item.outcome === "Discarded / Wasted") outcomeClass = "tag-wasted";
        if (item.outcome === "Used Internally") outcomeClass = "tag-requested";

        let actionBtn = "";
        if (item.status !== "Closed" && item.status !== "Removed") {
            actionBtn = `<button class="btn-outcome" onclick="openOutcomeModal(${item.id}, '${escapeHtml(item.foodName)}', ${item.remainingQuantity})">Update Outcome</button>`;
        } else {
            actionBtn = `<span style="font-size:12px; color:var(--muted-text);">Completed</span>`;
        }

        tr.innerHTML = `
            <td style="font-weight:700; color:var(--forest-green);">${escapeHtml(item.foodName)}</td>
            <td style="white-space:nowrap; font-size:12px;">${formatDate(item.createdAt)}</td>
            <td style="font-weight:700;">${item.initialQuantity} ${escapeHtml(item.unit)}</td>
            <td>${item.requestedQuantity}</td>
            <td style="color:#15803d; font-weight:700;">${item.deliveredQuantity}</td>
            <td style="font-weight:700;">${item.remainingQuantity}</td>
            <td><span class="status-tag ${statusClass}">${escapeHtml(item.status)}</span></td>
            <td><span class="status-tag ${outcomeClass}">${escapeHtml(item.outcome)}</span></td>
            <td>${actionBtn}</td>
        `;
        tbody.appendChild(tr);
    });
}

function openOutcomeModal(foodId, foodName, remainingQty) {
    document.getElementById("modalFoodId").value = foodId;
    document.getElementById("modalFoodName").textContent = foodName;
    document.getElementById("modalRemainingQty").textContent = remainingQty;
    document.getElementById("wastedQuantity").value = remainingQty;
    document.getElementById("wastedQuantity").max = remainingQty;
    document.getElementById("outcomeSelect").value = "Used Internally";
    document.getElementById("wasteQtyGroup").style.display = "none";
    document.getElementById("outcomeNotes").value = "";
    document.getElementById("outcomeModal").style.display = "flex";
}

function closeOutcomeModal() {
    document.getElementById("outcomeModal").style.display = "none";
}

function submitOutcomeForm(e) {
    e.preventDefault();
    const foodId = document.getElementById("modalFoodId").value;
    const outcome = document.getElementById("outcomeSelect").value;
    const wastedQty = document.getElementById("wastedQuantity").value;
    const notes = document.getElementById("outcomeNotes").value;

    const params = new URLSearchParams();
    params.append("foodId", foodId);
    params.append("outcome", outcome);
    params.append("wastedQuantity", wastedQty);
    params.append("notes", notes);

    fetch("smart-surplus", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: params.toString()
    })
    .then(res => res.json())
    .then(data => {
        if (data.success) {
            closeOutcomeModal();
            loadAnalytics();
        } else {
            alert(data.message || "Failed to update outcome");
        }
    })
    .catch(err => {
        console.error("Error updating outcome:", err);
        alert("An error occurred while updating outcome.");
    });
}

function formatDate(dateStr) {
    if (!dateStr) return "-";
    return dateStr.replace("T", " ").substring(0, 16);
}

function escapeHtml(text) {
    if (!text) return "";
    return String(text).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}
