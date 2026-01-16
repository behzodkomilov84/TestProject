document.addEventListener("DOMContentLoaded", () => {
    const topicIds = JSON.parse(sessionStorage.getItem("topicIds") || "[]");
    const limit = Number(sessionStorage.getItem("limit") || 10);
    const timeValue = Number(sessionStorage.getItem("time") || 10);

    if(topicIds.length === 0){
        alert("Нет выбранных тем. Вернитесь на предыдущую страницу.");
        window.location.href = "/"; // возвращаем на выбор
        return;
    }

    // Запрашиваем тест у backend
    fetch("/api/test-session/start", {
        method:"POST",
        headers: {"Content-Type":"application/json"},
        body: JSON.stringify({ topicIds, limit })
    })
        .then(r => r.json())
        .then(showTest);

    // Запускаем таймер
    startTimer(timeValue);
});



/*Хранилище состояния*/
let questions = [];
let index = 0;
let answers = {}; // questionId -> answerId

/*Отображение вопроса*/
function showTest(data){
    questions = data;
    index = 0;
    showQuestion();
    updateProgress();
}

function showQuestion(){
    const q = questions[index];

    let html = `<h3>${index+1}. ${q.text}</h3>`;

    q.answers.forEach(a=>{
        const checked = answers[q.id] === a.id ? "checked" : "";
        html += `
          <label>
            <input type="radio" name="q" value="${a.id}" ${checked}
                   onchange="answers[${q.id}] = ${a.id}">
            ${a.text}
          </label><br>`;
    });

    document.getElementById("questionBox").innerHTML = html;
}


/*Next / Prev*/
function next(){
    if(index < questions.length-1){
        index++;
        showQuestion();
        updateProgress();
    }
}

function prev(){
    if(index > 0){
        index--;
        showQuestion();
        updateProgress();
    }
}

/*Progress bar*/
function updateProgress(){
    const percent = ((index+1) / questions.length) * 100;
    document.getElementById("progress").style.width = percent + "%";
}

/*Таймер*/
let time;

function startTimer(min){
    time = min * 60;
    const interval = setInterval(()=>{
        const m = Math.floor(time/60);
        const s = time % 60;
        document.getElementById("timer").innerText = `${m}:${s<10?'0':''}${s}`;
        time--;

        if(time < 0){
            clearInterval(interval);
            finishTest();
        }
    },1000);
}

/*Завершение теста*/
function finishTest(){
    fetch("/api/tests/finish",{
        method:"POST",
        headers:{"Content-Type":"application/json"},
        body: JSON.stringify(answers)
    })
        .then(r=>r.json())
        .then(res=>{
            alert(`Score: ${res.correct}/${res.total}`);
        });
}

