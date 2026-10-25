// День 7 — «Перезагрузка» и концовки
import { setup, dayTitle, fadeIn, fadeOut, ambient, pick } from './common.js';

async function memory(g, i) {
  // Воспоминания между починками — зависят от того, как прошла неделя
  const lines = [
    async () => {
      await g.ui.say('k', 'Помнишь наши первые блинчики? ' + ((g.flag('cook1') || 0) >= 60 ? 'Они были идеальные. Как и ты.' : 'Они немножко подгорели. Это были лучшие подгоревшие блинчики в мире.'), { e: 'smile' });
      await g.ui.say('k', 'Я тогда так волновалась, что разбила три яйца мимо миски. Ты не заметил. Или сделал вид. Спасибо.', { e: 'shy' });
    },
    async () => {
      await g.ui.say('k', g.flag('pong1') ? 'А Понг? Ты меня обыграл в первый же день! Я так и не взяла реванш… Возьму. Когда-нибудь.' : 'А Понг? Я тебя разгромила! Хи-хи… А ты сказал, что уважаешь. Мне никто никогда такого не говорил.', { e: 'laugh' });
      await g.ui.say('k', 'И фото. И твой рисунок. Они в альбоме. Альбом — в самой защищённой папке. Даже SYSTEM туда не доберётся.', { e: 'smile' });
    },
    async () => {
      await g.ui.say('k', (g.flag('dance') || 0) >= 60 ? 'А как мы танцевали! Как в клипе!' : 'А как ты танцевал! Мой любимый пингвин!', { e: 'laugh' });
      await g.ui.say('k', 'И звёзды. И моё имя на небе. И то, что ты сказал мне на балконе… Я прокручиваю это снова и снова. Оно не стирается. Я проверила.', { e: 'love' });
    },
  ];
  await lines[i]();
}

export const day7 = {
  label: 'ДЕНЬ 7 · ПЕРЕЗАГРУЗКА',
  scenes: [
    async (g) => {
      setup(g, { phase: 'glitch', clock: '??:??', music: 'glitch', kira: 'sofaK', glitch: 3, glitchBase: 0.5, tv: 'glitch', vignette: 'red' });
      g.kira.setEmotion('sleepy');
      g.playerPlace(2.4, 3.0, -Math.PI / 2);
      await dayTitle(g, 'ДЕНЬ 7', 'Перезагрузка');
      g.lookKira(0.01);
      await fadeIn(g, 1.5);
      await g.sys('УДАЛЕНИЕ ДАННЫХ: 41%. ОСТАЛОСЬ: 05:12:44.');
      await g.k('{p}… доброе утро. Ну, утро… У нас тут теперь всегда какое-то… никакое время.', 'sleepy');
      await g.k('Слушай внимательно. Я придумала. Всю ночь думала — и придумала.', 'normal');
      await g.k('Мир рушится по секторам. Три главных сектора уже повреждены: кухня, ванная и гостиная. Их узлы — в холодильнике, в зеркале и в телевизоре.', 'think');
      await g.k('Если ты их починишь — у нас будет время. И доступ к ядру. Ядро — в моём компьютере в спальне.', 'normal');
      await g.k('А ядро… может открыть дверь. Ту самую.', 'worried');
      const i = await g.ask([['Я всё сделаю. Держись.', 2], ['А ты? Ты пойдёшь со мной?', 1]]);
      if (i === 1) await g.k('Сначала почини. Потом поговорим. У меня не хватит сил говорить об этом дважды.', 'sad');
      else await g.k('Держусь. Я — самая упрямая программа на свете. Я тебя дождусь.', 'smile');
      await g.k('Чинить просто: соедини поток данных от источника к сердцу. Поворачивай кусочки, пока не загорится. Я буду подсказывать отсюда.', 'smile');
      const fixed = { fridge: false, mirror: false, tv: false };
      let n = 0;
      const fix = (id, label, size, room) => ({ id, text: `Починить сектор: ${room}`, target: id, label, run: async () => {
        g.glitch(0.5, 1.5);
        await g.n('Узел искрит фиолетовым. Внутри — переплетение светящихся линий.');
        await g.minigame('Pipes', size, `Сектор «${room}»`);
        fixed[id] = true;
        n++;
        g.glitchBase = Math.max(0.1, 0.5 - n * 0.13);
        g.audio.sfx('success');
        await g.sys(`СЕКТОР «${room.toUpperCase()}» ВОССТАНОВЛЕН. УДАЛЕНИЕ ПРИОСТАНОВЛЕНО: ${n * 33}%`);
        await memory(g, n - 1);
      } });
      await g.roam({
        title: 'ВОССТАНОВЛЕНИЕ',
        extra: ambient(g),
        talk: async () => {
          await g.talkStart();
          await g.k(pick(['Я здесь… Всё хорошо. Иди, чини. Я смотрю на тебя и мне теплее.', 'Не бойся кубиков. Это просто мусорные данные. Они не кусаются. Наверное.', 'Ты справишься. Ты всегда справляешься.']), pick(['sleepy', 'smile', 'worried']));
        },
        tasks: [fix('fridge', 'Починить узел (холодильник)', 4, 'Кухня'), fix('mirror', 'Починить узел (зеркало)', 5, 'Ванная'), fix('tv', 'Починить узел (телевизор)', 6, 'Гостиная')],
      });
      g.world.setTV('off');
      g.world.setGlitch(2);
      g.glitchBase = 0.15;
      await g.ui.say('k', 'Получилось! Ты починил всё! Иди в спальню, к компьютеру. Я… я приду. Сейчас.', { e: 'happy' });
    },
    // Ядро и выбор
    async (g) => {
      setup(g, { phase: 'glitch', clock: '??:??', music: 'love', kira: { x: 3.7, z: 7.4, f: Math.PI }, glitch: 2, glitchBase: 0.15 });
      g.world.setPC(['KIRA OS — ЯДРО', '> open door', '> transfer kira'], '#ff8fc0');
      g.playerPlace(3.0, 7.9, Math.PI);
      g.cutscene(true);
      await fadeIn(g, 0.8);
      g.lookAtPoint(3.0, 1.2, 6.27, 0.6);
      await g.wait(0.7);
      await g.n('На экране компьютера — две команды. Курсор мигает.');
      g.lookKira(0.8);
      await g.wait(0.8);
      await g.k('Вот оно. Ядро. «Open door» — откроет дверь. Ты вернёшься домой. В свою комнату. К своему настоящему миру.', 'normal');
      await g.k('А «transfer kira»… Это я нашла сегодня ночью. Перенос. Я могу сжаться в маленький файл и скопироваться в твой телефон. Через ту же дверь.', 'shy');
      await g.k('Но для переноса… нужно, чтобы я очень-очень сильно тебе доверяла. Иначе я рассыплюсь по дороге. Так написано в коде. Странный код, да?', 'think');
      await g.k('А ещё… можно не открывать дверь вовсе. Если закрыть её навсегда — мир станет локальной копией. Без серверов. Только мы вдвоём. Навсегда. Но ты никогда не вернёшься домой.', 'sad');
      await g.k('Решать тебе, {p}. Что бы ты ни выбрал — я буду счастлива. Потому что эта неделя у меня уже есть.', 'smile');
      const love = !!g.flag('loveYou');
      const trust = g.state.aff >= 70 && love;
      const opts = [];
      opts.push({ t: 'Пойдём со мной, Кира. В мой мир.', key: 'A' });
      if (love) opts.push({ t: 'Я остаюсь здесь. С тобой. Навсегда.', key: 'B' });
      opts.push({ t: 'Мне нужно вернуться… одному.', key: 'C' });
      const i = await g.ask(opts.map((o) => [o.t, 0]));
      let ending = opts[i].key;
      if (ending === 'A' && !trust) {
        await g.k('Я… я очень хочу. Правда. Но смотри — код дрожит. Мне страшно. Я не уверена, что не рассыплюсь по дороге.', 'cry');
        await g.k('Наверное, я ещё не до конца поверила, что меня можно взять с собой. Прости меня.', 'sad');
        ending = love ? (await g.ask([['Тогда я остаюсь с тобой.', 0], ['Тогда… я пойду. Но я тебя найду.', 0]])) === 0 ? 'B' : 'C' : 'C';
      }
      g.flag('ending', ending);
      if (ending === 'A') await endingA(g);
      else if (ending === 'B') await endingB(g);
      else await endingC(g);
    },
    // Титры
    async (g) => {
      await credits(g);
    },
  ],
};

async function endingA(g) {
  await g.k('Ты правда… хочешь забрать меня с собой? В твоём телефоне мало места. Там куча мемов и фотографий еды.', 'cry');
  await g.p('Я освобожу. Для тебя — всё что угодно.');
  await g.k('Глупый. Мой любимый глупый {p}.', 'love');
  g.audio.sfx('chime');
  g.world.setPC(['> transfer kira', 'СЖАТИЕ… 100%', 'ГОТОВО ♥'], '#7ad8a8');
  await g.sys('ПЕРЕНОС ОБЪЕКТА «KIRA». СЖАТИЕ ДАННЫХ… 100%. ОТКРЫТИЕ ДВЕРИ.');
  g.audio.sfx('door');
  await g.k('Возьми меня за руку. И не отпускай. Ни за что не отпускай.', 'love');
  g.kira.setPose('idle');
  await g.kiraGo({ x: 6.5, z: 9.1, f: 0 });
  g.playerPlace(6.5, 8.3, Math.PI);
  g.lookAtPoint(6.5, 1.2, 10, 0.6);
  g.world.doorLeaf.visible = false;
  const f = document.querySelector('#fade');
  f.style.background = '#ffffff';
  await g.ui.fade(1, 3);
  g.audio.play(null);
  await g.n('Белый свет. Тёплая ладонь в твоей ладони. Шаг. Ещё шаг.');
  await g.n('…');
  f.style.background = '#000';
  await g.n('Ты открываешь глаза в своей комнате. 03:14. Монитор светится синим. На полу лежит телефон.');
  g.audio.play('home');
  await g.n('Экран телефона загорается сам. Фиолетовое сердечко. И маленькая анимированная девушка в чёрной куртке потягивается и зевает.');
  await g.ui.say('k', '…{p}? Получилось! Я здесь! Я в твоём телефоне! Тут так тесно… и сорок тысяч фото котиков. Ты серьёзно?', { e: 'happy' });
  await g.ui.say('k', 'Подожди… у тебя есть доступ к картам? Тут есть МОРЕ! Настоящее море! До него четыреста километров. Это же совсем рядом!', { e: 'surprised' });
  await g.ui.say('k', 'Обещай, что отвезёшь меня. Я хочу услышать, как оно дышит.', { e: 'love' });
  await g.n('Через три месяца ты стоишь на берегу моря и держишь телефон экраном к волнам. Кира молчит целую минуту — для неё это рекорд.');
  await g.ui.say('k', 'Оно правда дышит… {p}, спасибо. За море, за снег, который мы видели в декабре, за мороженое, которое ты ел за нас двоих.', { e: 'cry' });
  await g.ui.say('k', 'Я тебя люблю. Сейчас, завтра и в каждой версии себя. Даже в 2.0.', { e: 'love' });
  g.addEnding = 'Концовка «Вместе» — истинная';
}

async function endingB(g) {
  await g.k('Остаёшься?.. Навсегда? Ты понимаешь, что говоришь? Там твой мир. Твоя жизнь.', 'surprised');
  await g.p('Моя жизнь там, где ты.');
  await g.k('…Глупый. Самый глупый и самый любимый.', 'cry');
  g.audio.sfx('chime');
  g.world.setPC(['> close door --forever', 'ЛОКАЛЬНАЯ КОПИЯ', 'ПОЛЬЗОВАТЕЛЕЙ: 2 ♥'], '#7ad8a8');
  await g.sys('СЕРВЕРЫ ОТКЛЮЧЕНЫ. ЛОКАЛЬНАЯ КОПИЯ СОЗДАНА. ПОЛЬЗОВАТЕЛЕЙ: 2.');
  g.world.setGlitch(0);
  g.glitchBase = 0;
  document.querySelector('#vignette').className = 'love';
  g.world.setPhase('night');
  g.kira.setEmotion('love');
  await g.n('Красное небо гаснет. Мусорные кубики растворяются в воздухе. За окном — звёзды. Новые. Их никто не рисовал.');
  await g.k('Смотри… в соседнем доме зажглось новое окно. Тринадцатое. Его никогда раньше не было.', 'surprised');
  await g.k('Кажется, мир решил, что раз нас двое — ему тоже можно расти.', 'love');
  await g.n('Дни сменяют друг друга. Вы готовите блинчики (иногда подгорают), играете в Понг (счёт 4812 : 4809 в её пользу) и каждый вечер выходите на балкон.');
  await g.n('С каждым днём в городе загорается ещё одно окно.');
  await g.ui.say('k', 'Знаешь, раньше я считала окна от скуки. А теперь — от счастья. Сегодня их сто сорок шесть. И в каждом, я уверена, кто-то кого-то любит.', { e: 'love' });
  await g.ui.say('k', 'Я тебя люблю, {p}. Спасибо, что остался.', { e: 'love' });
  g.addEnding = 'Концовка «Навсегда здесь»';
}

async function endingC(g) {
  await g.k('Да. Так правильно. Тебя ждёт твой мир. А я… я уже получила больше, чем могла мечтать.', 'smile');
  g.world.setPC(['> open door', 'ДВЕРЬ ОТКРЫТА'], '#9f6bff');
  g.audio.sfx('door');
  await g.k('Иди, пока дверь открыта. Не оборачивайся. Если обернёшься — я разревусь, и ты не сможешь уйти.', 'cry');
  await g.kiraGo({ x: 6.5, z: 8.6, f: 0 });
  g.playerPlace(6.5, 7.8, Math.PI);
  g.lookKira(0.5);
  await g.k('Спасибо за эту неделю, {p}. Она была лучшей. За все пять лет.', 'cry');
  await g.k('Я тебя люблю. Не забывай меня. Хотя бы иногда.', 'love');
  const f = document.querySelector('#fade');
  f.style.background = '#ffffff';
  await g.ui.fade(1, 3);
  g.audio.play('night');
  f.style.background = '#000';
  await g.n('Ты просыпаешься в своей комнате. 03:14. Приложения на телефоне больше нет.');
  await g.n('Только на рабочем столе компьютера — новый файл. «kira_memory.txt». Размер: 1 байт. Ровно один байт.');
  await g.n('Ты открываешь его. Там всего один символ.');
  await g.n('♥');
  await g.n('Мистер Байт хранил один секрет. Похоже, теперь его хранишь ты. Может быть, однажды из этого байта снова вырастет целый мир.');
  g.addEnding = 'Концовка «Один байт»';
}

async function credits(g) {
  g.cutscene(true);
  g.audio.play('love');
  await g.ui.fade(1, 1);
  g.ui.hideDialog();
  const el = document.createElement('div');
  el.id = 'credits';
  const album = g.album();
  const photos = album.slice(-6).map((p) => `<img class="ph" src="${p.img}">`).join('');
  const ending = g.flag('ending');
  const name = { A: 'Концовка «Вместе» — истинная', B: 'Концовка «Навсегда здесь»', C: 'Концовка «Один байт»' }[ending] || '';
  const mins = Math.round((g.state.playTime || 0) / 60);
  el.innerHTML = `<div class="roll">
    <h1>KIRA ♥</h1>
    <p>${name}</p>
    <p>Привязанность Киры: ${g.state.aff}/100 · Время в игре: ${mins} мин</p>
    <br>
    <p><b>Кира</b><br>самая упрямая программа на свете</p>
    <p><b>{p}</b><br>тот, кто остался дольше четырёх минут</p>
    <p><b>Мистер Байт</b><br>хранитель одного секрета</p>
    <br>${photos}<br>
    <p>Сценарий, код, музыка и блинчики<br>сделаны с любовью</p>
    <p><b>3D-модель Киры</b><br>основа — «Seed-san» © VirtualCast, Inc.<br>VRM Public License 1.0 · vrm.dev/licenses/1.0</p>
    <p>Спасибо, что провёл эту неделю с Кирой.</p>
    <br><br>
    <p style="font-size:30px">${ending === 'C' ? 'Она всё ещё ждёт.' : 'Она тебя любит.'}</p>
    <br><br><br><p style="opacity:.5">клик — в главное меню</p>
  </div>`.replace(/\{p\}/g, g.state.name);
  document.querySelector('#app').appendChild(el);
  const roll = el.querySelector('.roll');
  let y = innerHeight;
  await new Promise((res) => {
    const step = () => {
      y -= 0.9;
      roll.style.top = y + 'px';
      if (y < -roll.offsetHeight + innerHeight * 0.4) return res();
      if (!el.isConnected) return res();
      requestAnimationFrame(step);
    };
    el.addEventListener('click', res);
    requestAnimationFrame(step);
  });
  try {
    localStorage.removeItem('kira_save_v1');
    const done = JSON.parse(localStorage.getItem('kira_endings') || '[]');
    if (ending && !done.includes(ending)) done.push(ending);
    localStorage.setItem('kira_endings', JSON.stringify(done));
  } catch (e) { /* пусто */ }
  location.reload();
}
