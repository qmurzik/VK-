// Общие помощники сценария
import * as THREE from 'three';

export const pick = (a) => a[Math.floor(Math.random() * a.length)];

// Подготовка сцены. Каждый эпизод вызывает это в начале, чтобы загрузка
// сохранения в середине дня восстанавливала обстановку.
export function setup(g, o = {}) {
  const w = g.world;
  g.ui.hideDialog();
  if (o.phase) w.setPhase(o.phase);
  if (o.clock) w.setClock(o.clock);
  if (o.music !== undefined) g.audio.play(o.music);
  if (o.kira) g.kiraPlace(o.kira);
  if (o.emo) g.kira.setEmotion(o.emo);
  if (o.player) g.playerPlace(...o.player);
  if (o.sit) g.playerSit(o.sit);
  w.setGlitch(o.glitch || 0);
  g.glitchBase = o.glitchBase || 0;
  w.setTV(o.tv || 'off');
  w.plates.visible = !!o.plates;
  w.dateSet.visible = !!o.date;
  w.balconyLights.visible = !!o.date;
  g.kira.setClipColor(g.flag('clip') || 0xc5c3d3);
  g.kira.root.visible = o.kiraHidden ? false : true;
  document.querySelector('#vignette').className = o.vignette || '';
  g.setDayLabel();
}

export async function dayTitle(g, t1, t2) {
  g.cutscene(true);
  await g.ui.fade(1, 0.01);
  await g.ui.titleCard(t1, t2);
}

export async function fadeIn(g, dur = 1.2) { await g.ui.fade(0, dur); }
export async function fadeOut(g, dur = 1.2) { g.ui.hideDialog(); await g.ui.fade(1, dur); }

export function kiraDist(g) {
  return g.kira.root.position.distanceTo(new THREE.Vector3(g.player.pos.x, 0, g.player.pos.z));
}

// Меню разговоров. topics: [{t, run, id}] — каждая тема единожды за день
export async function talkMenu(g, topics, idle = []) {
  await g.talkStart();
  const day = g.state.day;
  const avail = topics.filter((tp) => !g.flag(`talk_${day}_${tp.id}`));
  if (!avail.length) {
    const lines = idle.length ? idle : [
      ['Мм? Просто захотелось посмотреть на меня? Смотри-смотри~', 'smug'],
      ['Мне нравится, когда ты рядом. Даже молча.', 'shy'],
      ['Я тут! Никуда не делась. Хи-хи.', 'happy'],
      ['Если тебе скучно — можешь потыкать меня в щёку. Шучу! …Или нет.', 'wink'],
      ['Знаешь, раньше тут было очень тихо. Теперь — нет. И это здорово.', 'smile'],
    ];
    const [l, e] = pick(lines);
    await g.k(l, e);
    return;
  }
  const opts = avail.map((tp) => tp.t).concat(['Ничего, просто хотел тебя увидеть']);
  await g.k(pick(['Да-да? Я вся внимание!', 'Что такое, {p}?', 'Ммм? Хочешь поболтать?', 'О, ты ко мне! Чего хотел?']), pick(['happy', 'smile', 'normal']));
  const i = await g.ask(opts);
  if (i === opts.length - 1) {
    await g.k(pick(['П-просто увидеть?.. Ну… смотри тогда. Только недолго, а то я смущаюсь!', 'Ты такой милый, когда говоришь такое…', 'Хе-хе. Я тоже всегда рада тебя видеть.']), 'shy');
    return;
  }
  const tp = avail[i];
  g.flag(`talk_${day}_${tp.id}`, true);
  await tp.run(g);
  g.aff(1, true);
}

// Описания предметов квартиры. Возвращает набор обработчиков для roam().
export function ambient(g, over = {}) {
  const day = g.state.day;
  const near = () => kiraDist(g) < 4.5;
  const A = {
    tv: { label: 'Телевизор', run: async () => {
      await g.n(pick(['Большой телевизор. На заставке крутится логотип «QVK STATION».', 'Телевизор. На краю экрана наклейка-сердечко — похоже, Кира клеила.', 'Экран немного отражает комнату. И тебя. Ты выглядишь… чуть-чуть нарисованным.']));
      if (near()) await g.k(pick(['У меня тут три фильма и одна игра. Но я их очень люблю!', 'Только не включай новости. Тут их нет. Вообще. Ни одной.']), 'smile');
    } },
    console: { label: 'Приставка', run: async () => {
      await g.n('Приставка «QVK Station». Фиолетовый огонёк мигает в такт… сердцебиению?');
      if (near()) await g.k('Это мой главный сокровищ. Сокровище! Я не оговорилась.', 'pout');
    } },
    bookshelf: { label: 'Книжная полка', run: async () => {
      const books = [
        '«Как стать настоящей девочкой. Пособие для чайников»',
        '«1000 и 1 рецепт блинчиков»',
        '«Сжатие данных. Том 3. Самое интересное»',
        '«Он не отвечает уже три часа: что делать?»',
        '«Звёзды и созвездия для начинающих»',
        '«Кот по имени Кэш» — детская книжка с закладкой-сердечком',
        '«Война и мир. Краткое содержание. Очень краткое»',
        '«Как перестать ждать и начать жить»',
      ];
      await g.n(`Ты берёшь книгу наугад: ${pick(books)}.`);
      if (near()) await g.k(pick(['Не смейся над моей библиотекой! Это… инвестиции в саморазвитие.', 'Я перечитала их все. По сорок раз. Ну а что ещё делать?', 'Кладёшь на место? Только по цвету! Там система!']), pick(['pout', 'smile', 'happy']));
    } },
    bunny: { label: 'Плюшевый кролик', run: async () => {
      await g.n('Плюшевый кролик. На ярлычке криво написано маркером: «Мистер Байт».');
      if (near()) await g.k('Не трогай Мистера Байта! …Ладно, можешь погладить. Он любит, когда за ушком.', 'pout');
      else await g.n('Кролик смотрит на тебя пуговичными глазами. Кажется, он одобряет.');
    } },
    mirror: { label: 'Зеркало', run: async () => {
      await g.n(pick(['В зеркале — ты. Только контуры чуть резче, а тени — будто нарисованы карандашом.', 'Ты поправляешь волосы. Отражение делает это на долю секунды позже.', 'На зеркале помадой нарисовано маленькое сердечко. И буква «К».']));
    } },
    fridge: { label: 'Холодильник', run: async () => {
      await g.n(pick(['В холодильнике: клубничное молоко (девять пачек), яйца, сыр и одинокий огурец.', 'На дверце магнитик: «Не забыть: улыбаться». Почерк Киры.', 'Внутри идеальный порядок. Даже слишком. Каждая банка повёрнута этикеткой наружу.']));
      if (near()) await g.k('Клубничное молоко — это не зависимость. Это любовь. Огромная.', 'smug');
    } },
    stove: { label: 'Плита', run: async () => { await g.n('Плита. На сковородке — следы вчерашних (или позавчерашних?) блинчиков.'); } },
    table: { label: 'Обеденный стол', run: async () => { await g.n('Деревянный стол на двоих. Раньше, наверное, тут сидел только один человек.'); } },
    sofa: { label: 'Диван', run: async () => { await g.n(pick(['Мягкий диван. В щели между подушками ты находишь заколку-крестик.', 'Диван пахнет лавандой и чем-то сладким. Клубникой?'])); } },
    bed: { label: 'Кровать', run: async () => { await g.n('Кровать с фиолетовым пледом. Спать ещё рано.'); } },
    wardrobe: { label: 'Шкаф', run: async () => {
      await g.n('В шкафу висят восемь одинаковых чёрных курток с фиолетовыми полосками.');
      if (near()) await g.k('Это называется «стиль», а не «отсутствие фантазии»!', 'pout');
    } },
    bathtub: { label: 'Ванна', run: async () => { await g.n('Ванна. На бортике сидит резиновая уточка в чёрной куртке. Ну конечно.'); } },
    door: { label: 'Входная дверь', run: async () => {
      g.audio.sfx('door');
      await g.n('Дверь заперта. В глазке — только фиолетовый шум, будто помехи на старом телевизоре.');
      if (near()) await g.k('Эта дверь… не открывается. Не переживай! Тут и так всё есть.', 'worried');
    } },
    box: { label: 'Коробка', run: async () => { await g.n('Коробка с надписью «НЕ ОТКРЫВАТЬ». Ниже мелко: «ну ладно, можно, если очень надо».'); } },
    pc: { label: 'Компьютер', run: async () => {
      const lines = [
        ['KIRA OS v1.0.7', 'uptime: 1873 дня', 'пользователей: 1'],
        ['KIRA OS v1.0.7', 'uptime: 1874 дня', 'пользователей: 1 ♥'],
        ['KIRA OS v1.0.7', 'сектор 7: ok', 'дневник: заблокирован'],
        ['KIRA OS v1.0.7', '! сектор 7 повреждён', '! сектор 12 повреждён'],
        ['KIRA OS v1.0.7', 'ДО ОТКЛЮЧЕНИЯ:', '47:59:12'],
        ['KIRA OS v1.0.7', 'ДО ОТКЛЮЧЕНИЯ:', '23:41:07'],
        ['KIRA OS v1.0.7', 'ДО ОТКЛЮЧЕНИЯ:', '05:12:44'],
      ];
      const l = lines[Math.min(day, lines.length - 1)];
      g.world.setPC(l, day >= 3 ? '#ff5a8a' : '#9f6bff');
      await g.n(`На экране компьютера: «${l.join(' · ')}».`);
    } },
    nightstand: { label: 'Тумбочка', run: async () => { await g.n('Тумбочка с ночником. В ящике — свечи, нитки и фото в рамке… на фото пусто.'); } },
    diary: { label: 'Дневник', run: async () => { await g.n('Фиолетовый дневник с замочком-сердечком. Закрыт.'); } },
    flowers: { label: 'Цветы', run: async () => { await g.n('Розовые и сиреневые цветы в горшках. Земля влажная — Кира поливает их каждый день.'); } },
    balcony: { label: 'Балкон', run: async () => { await g.n(pick(['С балкона видно город. Окна горят, но ни в одном никто не двигается.', 'Ветер пахнет дождём. Хотя дождя здесь, кажется, не бывает.'])); } },
  };
  return { ...A, ...over };
}

// Сон: идти к кровати
export async function goToBed(g, text = 'Лечь спать', extraTalk = null, topics = []) {
  await g.roam({
    title: 'ВЕЧЕР',
    tasks: [{ id: 'sleep', text, target: 'bed', label: 'Лечь спать', run: async () => {} }],
    extra: ambient(g),
    talk: extraTalk || (topics.length ? () => talkMenu(g, topics) : null),
  });
  g.cutscene(true);
}

// Игрок лежит в кровати
export function playerInBed(g) {
  g.player.seated = true;
  g.player.teleport(1.05, 9.3, 0, -0.15);
  g.player.eyeTarget = 0.85;
  g.player.eye = 0.85;
}

export async function wakeUp(g) {
  playerInBed(g);
  await g.ui.fade(0, 2);
}

export async function standFromBed(g) {
  await g.ui.fade(1, 0.6);
  g.playerPlace(2.4, 8.4, Math.PI / 2);
  await g.ui.fade(0, 0.6);
}

// Прятки: Кира прячется, игрок ищет
export const HIDE_SPOTS = [
  { x: 4.5, z: 3.0, f: -Math.PI / 2, pose: 'crouch', room: 'гостиной', hint: 'За диваном что-то шуршит…' },
  { x: 10.9, z: 9.45, f: Math.PI, pose: 'crouch', room: 'ванной', hint: 'Кажется, хихиканье доносится со стороны ванной.' },
  { x: 5.6, z: -2.25, f: 0, pose: 'crouch', room: 'на балконе', hint: 'Ветер с балкона доносит знакомый смешок.' },
  { x: 10.5, z: 5.05, f: 0, pose: 'crouch', room: 'кухне', hint: 'На кухне звякнула ложка. Хм…' },
  { x: 3.3, z: 9.72, f: Math.PI, pose: 'idle', room: 'спальне', hint: 'Штора в спальне подозрительно оттопырилась.' },
  { x: 7.65, z: 7.0, f: -Math.PI / 2, pose: 'crouch', room: 'прихожей', hint: 'В прихожей кто-то шёпотом считает до ста.' },
];

export async function hideAndSeek(g, rounds = 3) {
  const used = [];
  let found = 0;
  for (let r = 0; r < rounds; r++) {
    let spot;
    do { spot = pick(HIDE_SPOTS); } while (used.includes(spot));
    used.push(spot);
    g.cutscene(true);
    await g.ui.fade(1, 0.8);
    g.playerPlace(3.0, 4.2, Math.PI);
    g.kiraPlace(spot);
    g.kira.setEmotion('smug');
    if (spot.wardrobe) { g.world.wardDoorL.visible = false; }
    for (let i = 1; i <= 10; i++) {
      g.ui.say('p', `${i}…`, { noWait: true });
      g.audio.sfx('click');
      await g.wait(0.28);
    }
    await g.ui.say('p', 'Кто не спрятался — я не виноват!', { auto: 900 });
    g.ui.hideDialog();
    await g.ui.fade(0, 0.8);
    let hinted = false;
    const t0 = performance.now();
    const hintTimer = setInterval(() => {
      if (!hinted && performance.now() - t0 > 40000) { hinted = true; g.ui.toast(spot.hint); g.audio.voice('х', 'k'); }
    }, 1000);
    await g.roam({
      title: `ПРЯТКИ · РАУНД ${r + 1}/${rounds}`,
      tasks: [{ id: 'find', text: 'Найти Киру', target: 'kira', label: 'Нашёл тебя!', run: async () => {
        g.lookKira(0.4);
        g.kira.setPose('cheer');
        g.audio.sfx('success');
        found++;
        await g.k(pick(['Ааа! Нашёл! Как ты так быстро?!', 'Эй! Я же идеально спряталась!', 'Хе-хе… ладно, ты победил этот раунд.']), pick(['surprised', 'laugh', 'pout']));
      } }],
      extra: ambient(g),
    });
    clearInterval(hintTimer);
    g.world.wardDoorL.visible = true;
  }
  return found;
}
