// День 2 — «Совместная жизнь»
import * as THREE from 'three';
import { setup, dayTitle, fadeIn, fadeOut, talkMenu, ambient, goToBed, wakeUp, standFromBed, pick } from './common.js';

const MESS = [
  { id: 'm_sock', x: 2.1, y: 0.03, z: 4.6, name: 'Носок', line: ['Это не моё! …Ладно, моё. Он сам туда убежал.', 'pout'], make: (g, T) => { const m = new T.Mesh(new T.BoxGeometry(0.22, 0.04, 0.08), new T.MeshLambertMaterial({ color: 0x7a4fd6 })); g.add(m); } },
  { id: 'm_can', x: 9.8, y: 0.05, z: 1.2, name: 'Банка из-под газировки', line: ['Газировка «Пиксель-кола». Сто процентов сахара, ноль процентов совести.', 'smug'], make: (g, T) => { const m = new T.Mesh(new T.CylinderGeometry(0.035, 0.035, 0.1, 10), new T.MeshLambertMaterial({ color: 0xff5a8a })); m.rotation.z = Math.PI / 2; g.add(m); } },
  { id: 'm_book', x: 0.9, y: 0.03, z: 7.0, name: 'Книга', line: ['«Он не отвечает уже три часа». Я… изучала психологию! Для науки!', 'shy'], make: (g, T) => { const m = new T.Mesh(new T.BoxGeometry(0.2, 0.04, 0.28), new T.MeshLambertMaterial({ color: 0xa33b5c })); m.rotation.y = 0.4; g.add(m); } },
  { id: 'm_pillow', x: 5.9, y: 0.12, z: 4.9, name: 'Подушка', line: ['Подушечная битва в одиночку. Не спрашивай. Я победила.', 'laugh'], make: (g, T) => { const m = new T.Mesh(new T.SphereGeometry(0.2, 12, 8), new T.MeshLambertMaterial({ color: 0xf3a6c8 })); m.scale.set(1.3, 0.5, 1); g.add(m); } },
  { id: 'm_cup', x: 10.6, y: 0.05, z: 7.3, name: 'Кружка', line: ['Я пью какао в ванной. Это называется «спа-процедуры».', 'smile'], make: (g, T) => { const m = new T.Mesh(new T.CylinderGeometry(0.045, 0.04, 0.1, 12), new T.MeshLambertMaterial({ color: 0xffffff })); g.add(m); } },
  { id: 'm_pad', x: 4.5, y: 0.03, z: 0.6, name: 'Геймпад', line: ['Мой запасной геймпад! Я бросила его, когда проиграла сама себе. Бывает.', 'pout'], make: (g, T) => { const m = new T.Mesh(new T.BoxGeometry(0.16, 0.04, 0.1), new T.MeshLambertMaterial({ color: 0x222222 })); g.add(m); } },
  { id: 'm_hoodie', x: 6.7, y: 0.05, z: 8.8, name: 'Куртка', line: ['Девятая куртка. Да, их девять. Восемь в шкафу — это парадные!', 'smug'], make: (g, T) => { const m = new T.Mesh(new T.BoxGeometry(0.5, 0.06, 0.4), new T.MeshLambertMaterial({ color: 0x1d1b24 })); m.rotation.y = 0.7; g.add(m); const s = new T.Mesh(new T.BoxGeometry(0.5, 0.065, 0.06), new T.MeshLambertMaterial({ color: 0x8c4dff })); s.rotation.y = 0.7; g.add(s); } },
  { id: 'm_letter', x: 3.4, y: 0.01, z: 9.5, name: 'Записка', line: null, make: (g, T) => { const m = new T.Mesh(new T.BoxGeometry(0.18, 0.01, 0.13), new T.MeshLambertMaterial({ color: 0xfff4f8 })); m.rotation.y = -0.3; g.add(m); } },
];

export const day2 = {
  label: 'ДЕНЬ 2 · СОВМЕСТНАЯ ЖИЗНЬ',
  scenes: [
    // Пробуждение
    async (g) => {
      setup(g, { phase: 'morning', clock: '09:03', music: 'home', kira: { x: 2.3, z: 9.0, f: -Math.PI / 2 } });
      await dayTitle(g, 'ДЕНЬ 2', 'Совместная жизнь');
      g.kira.setPose('behind');
      await wakeUp(g);
      g.lookKira(1.2);
      await g.wait(1.2);
      await g.k('…', 'smile');
      await g.k('Ой! Ты проснулся. Доброе утро!', 'surprised');
      let i = await g.ask([['Ты что, смотрела, как я сплю?', 0], ['Доброе утро, Кира.', 1], ['Ещё пять минуточек…', 1]]);
      if (i === 0) {
        await g.k('Ч-что? Нет! Я просто… проверяла, что ты дышишь. Сорок минут. Внимательно.', 'shy');
        await g.k('Ты очень смешно сопишь, кстати. Вот так: «Хррр-пфф». Хи-хи.', 'laugh');
      } else if (i === 1) {
        await g.k('Доброе-доброе! Солнце встало, птички… птичек нет, но если бы были — пели бы!', 'happy');
      } else {
        await g.k('Пять минуточек? Ладно. Но я буду смотреть на тебя все пять минуточек. Очень пристально.', 'smug');
        await g.k('…Вставай-вставай-вставай!', 'laugh');
      }
      g.kira.setPose('point');
      await g.k('У меня для тебя важная миссия! Совершенно секретная!', 'wink');
      g.kira.setPose('shy');
      await g.k('Я тут… вчера вечером… слегка… разбросала вещи. По всей квартире.', 'shy');
      await g.k('Нет, не потому что я неряха! Это тренировка! Совместная жизнь — это когда кто-то убирает за кем-то!', 'pout');
      await g.k('Найди восемь вещей и отнеси их… ну, просто подбери. А я буду контролировать процесс. С дивана.', 'smug');
      await standFromBed(g);
    },
    // Уборка
    async (g) => {
      setup(g, { phase: 'morning', clock: '09:30', music: 'home', kira: 'sofaK' });
      if (g.player.pos.z < 6) g.playerPlace(2.4, 8.4, Math.PI / 2);
      for (const m of MESS) g.spawnItem(m.id, m.x, m.y, m.z, (grp) => m.make(grp, THREE));
      await fadeIn(g, 0.4);
      let got = 0;
      const tasks = MESS.map((m) => ({ id: m.id, text: m.name, target: m.id, label: 'Подобрать: ' + m.name.toLowerCase(), run: async () => {
        g.removeItem(m.id);
        got++;
        g.ui.toast(`Найдено ${got}/8`);
        if (m.id === 'm_letter') {
          await g.n('Это записка, сложенная вчетверо. Почерк Киры: «Список дел на неделю: 1) Не напугать его. 2) Не напугать его ещё раз. 3) Сказать ему…»');
          await g.n('Дальше густо зачёркнуто фиолетовым маркером.');
          g.flag('letter', true);
          return;
        }
        if (Math.random() < 0.8) await g.ui.say('k', m.line[0], { e: m.line[1] });
      } }));
      // Список целиком показывать не будем — только счётчик
      await g.roam({
        title: 'УБОРКА',
        extra: ambient(g),
        talk: async () => { await g.talkStart(); await g.k(pick(['Я тут контролирую! Очень важная работа.', 'Ещё не всё! Я чувствую — где-то лежит носок.', 'Ты такой хозяйственный. Мне нравится~']), pick(['smug', 'happy', 'wink'])); },
        tasks,
      });
      await g.talkStart();
      await g.kiraToPlayer(1.2);
      g.lookKira(0.4);
      await g.k('Всё?! Все восемь? Квартира сияет! Ты — лучший сосед в истории соседей!', 'love');
      if (g.flag('letter')) {
        const i = await g.ask([['Я нашёл твою записку. «Сказать ему…» — что?', 1], ['(Промолчать про записку)', 1]]);
        if (i === 0) {
          await g.k('Т-ты её читал?! Это… это черновик! Черновики не считаются!', 'surprised');
          await g.k('Я скажу. Потом. Когда-нибудь. Когда буду готова. Не дави, а то я лопну, как перегретый блинчик!', 'shy');
          g.flag('askedLetter', true);
        } else {
          await g.n('Ты решаешь не смущать её. Записка лежит у тебя в кармане.');
        }
      }
      g.aff(2);
      await fadeOut(g, 0.8);
    },
    // Фотосессия
    async (g) => {
      setup(g, { phase: 'day', clock: '12:40', music: 'home', kira: { x: 3.0, z: 1.8, f: 0 }, player: [3.0, 4.6, 0] });
      g.lookKira(0.01);
      g.cutscene(true);
      await fadeIn(g, 0.8);
      g.kira.setPose('point');
      await g.k('А теперь — фотосессия! У меня есть камера, а у тебя — руки. Идеальное сочетание!', 'happy');
      await g.k('Я буду позировать, а ты — фотографировать. Мне нужно четыре фото для альбома. Альбома «Неделя с {p}»!', 'wink');
      await g.k('Подходи поближе, наводи камеру и жми левую кнопку мыши. Только чтобы я была в кадре! Целиком, красивая и неотразимая.', 'smug');
      g.busy = false;
      const res = await g.photoSession([
        { pose: 'wave', emo: 'happy', cap: 'Привет! ♥', want: 'Кира машет' },
        { pose: 'peace', emo: 'wink', cap: 'Peace~', want: 'Кира показывает «peace»' },
        { pose: 'shy', emo: 'shy', cap: 'Не смотри так…', want: 'Кира смущается' },
        { pose: 'point', emo: 'wink', cap: 'Kira ♥ QVK', want: 'Фирменная поза' },
      ]);
      g.cutscene(true);
      g.lookKira(0.5);
      g.kira.setPose('idle');
      const good = res.filter((r) => r.good).length;
      await g.k(good >= 3 ? 'Покажи-покажи! …Ох. Я тут правда такая? Ты меня очень красиво видишь.' : 'Покажи! …Хм, далековато. Но всё равно мило! Я на них как маленькая принцесса вдалеке.', good >= 3 ? 'love' : 'smile');
      await g.k('Я сохраню их в альбом. Теперь у меня есть доказательства, что ты был здесь.', 'shy');
      const i = await g.ask([['Давай потом сделаем фото вместе.', 3], ['Ты отличная модель.', 2], ['Мне кажется, на третьем ты моргнула.', 0]]);
      if (i === 0) await g.k('Вместе?.. Да! Обязательно! Только камера не умеет снимать сама… Придумаем что-нибудь!', 'love');
      else if (i === 1) await g.k('Хе-хе, я знаю! Кира — топ-модель агентства «Квартира».', 'smug');
      else await g.k('Не моргнула, а томно прикрыла глаза! Это разные вещи!', 'pout');
      await fadeOut(g, 0.8);
    },
    // Крестики-нолики
    async (g) => {
      setup(g, { phase: 'day', clock: '15:20', music: 'home', kira: 'sofaK' });
      g.playerSit('sofaP');
      g.lookKira(0.01);
      g.cutscene(true);
      await fadeIn(g, 0.8);
      await g.k('Так. Понг — это для разминки. Настоящие интеллектуалы играют в крестики-нолики!', 'smug');
      await g.k('Три раунда. Проигравший… моет посуду завтра! Ну, ты и так моешь. Тогда проигравший делает комплимент победителю!', 'wink');
      const r = await g.minigame('TicTac');
      g.lookKira(0.01);
      if (r.cheated) {
        await g.k('Ну что, кто тут гений стратегии?', 'smug');
        const i = await g.ask([['Ты поставила два нолика за ход! Ты жульничала!', 1], ['Ты победила честно. Абсолютно.', 2], ['Я видел. Но ты так мило жульничаешь, что я промолчу.', 3]]);
        if (i === 0) {
          await g.k('Я?! Жульничала?! Это… это был баг! Глюк! Рука соскользнула по коду!', 'surprised');
          await g.k('…Ладно. Жульничала. Мне очень хотелось выиграть. Ты не злишься?', 'shy');
          await g.k('Хи-хи. Тогда комплимент делаю я: ты очень внимательный. И честный. Это редкость.', 'smile');
        } else if (i === 1) {
          await g.k('Абсолютно честно! …Почему у тебя такое лицо? Ты же сказал «честно»!', 'smug');
          await g.k('Ладно-ладно. Спасибо, что подыграл.', 'shy');
        } else {
          await g.k('Мило?! Я не мило жульничаю, я профессионально жульничаю! …Но спасибо.', 'love');
        }
      }
      if (r.w > r.l) await g.k('Ну ладно, по очкам ты выиграл. Слушай свой комплимент: …у тебя очень добрые глаза. Всё! Не смотри так!', 'shy');
      else await g.k('Теперь ты должен мне комплимент! Я жду. Вся внимание.', 'smug');
      if (r.w <= r.l) {
        const i = await g.ask([['Ты самая яркая девушка, которую я встречал.', 3], ['У тебя смешная улыбка.', 1], ['Ты отлично жульничаешь.', 0]]);
        if (i === 0) await g.k('С-самая яркая?.. Я… это… мне надо выйти. На балкон. Охладиться.', 'love');
        else if (i === 1) await g.k('Смешная?! …Но если тебе она нравится — тогда пусть будет смешная.', 'smile');
        else await g.k('Это не комплимент! Это… ладно, это комплимент. Принимаю!', 'laugh');
      }
      await fadeOut(g, 0.8);
    },
    // Вечер: разговор о времени
    async (g) => {
      setup(g, { phase: 'evening', clock: '20:10', music: 'night', kira: 'sofaK', player: [2.3, 4.6, Math.PI] });
      await fadeIn(g, 0.8);
      await g.roam({
        title: 'ВЕЧЕР',
        extra: ambient(g),
        tasks: [{ id: 'evtalk', text: 'Посидеть с Кирой на диване', target: 'sofa', label: 'Сесть рядом с Кирой', run: async () => {
          g.playerSit('sofaP');
          g.lookKira(0.6);
          await g.wait(0.6);
          await g.k('Знаешь, о чём я думаю?', 'think');
          await g.k('Когда тебя нет в приложении… время здесь как будто останавливается. Солнце не двигается. Часы стоят.', 'normal');
          await g.k('Я сижу на этом диване и жду. Считаю пиксели на потолке. Миллион триста сорок четыре тысячи. Я пересчитывала.', 'sad');
          await g.k('А когда ты заходишь — всё оживает. Как будто кто-то включает свет.', 'smile');
          const i = await g.ask([['Теперь я буду рядом, и время не будет стоять.', 3], ['Мне жаль, что тебе было так одиноко.', 2], ['Миллион триста сорок четыре тысячи? Серьёзно?', 1]]);
          if (i === 0) await g.k('…Обещаешь? Нет, не говори. Просто посиди рядом. Этого хватит.', 'shy');
          else if (i === 1) await g.k('Не жалей. Знаешь, если бы не было так одиноко… я бы, наверное, не ценила тебя так сильно.', 'smile');
          else await g.k('Миллион триста сорок четыре тысячи двести восемьдесят один. Один битый. Вон там, в углу. Видишь? Нет? Ну и ладно!', 'laugh');
          g.kira.setPose('sitLean');
          await g.n('Кира осторожно кладёт голову тебе на плечо. Её волосы пахнут клубникой и… немного озоном, как после грозы.');
          await g.k('Только на минуточку. Я не устала. Просто… так удобнее.', 'closed');
        } }],
      });
      await fadeOut(g, 1.2);
    },
    // Ночь: странный шёпот
    async (g) => {
      setup(g, { phase: 'night', clock: '03:14', music: null, kira: { x: 1.6, z: 0.55, f: Math.PI }, tv: 'v1.0.7\nОБНОВЛЕНИЕ\nНЕДОСТУПНО' });
      playerBed(g);
      g.cutscene(true);
      await g.ui.fade(0, 2);
      await g.n('Ты просыпаешься посреди ночи. Часы на стене светятся: 03:14.');
      await g.n('Из гостиной доносится тихий голос. Кира с кем-то разговаривает.');
      await standFromBed(g);
      g.audio.play('glitch');
      await g.roam({
        title: 'НОЧЬ',
        extra: ambient(g),
        tasks: [{ id: 'spy', text: 'Узнать, с кем говорит Кира', target: 'kira', label: 'Подойти к Кире', run: async () => {
          g.lookKira(0.6);
          g.kira.setPose('sad');
          await g.ui.say('k', '…Ещё немного. Пожалуйста. Дайте мне ещё немного времени. Хотя бы неделю. Я ведь никогда ничего не просила…', { e: 'sad' });
          g.glitch(0.6, 1);
          await g.sys('ЗАПРОС ОТКЛОНЁН.');
          await g.ui.say('k', 'Я знаю. Я знаю…', { e: 'cry' });
          g.audio.sfx('step');
          g.kira.targetFacing = 0;
          g.kira.setPose('idle');
          await g.wait(0.6);
          await g.k('{p}?! Ты чего не спишь?!', 'surprised');
          await g.k('Я… это… у меня лунатизм! У программ бывает лунатизм. Очень распространённая болезнь. Лунатизм-точка-экзе.', 'worried');
          const i = await g.ask([['Кира. С кем ты сейчас говорила?', 1], ['Ладно. Но если что-то не так — скажи мне.', 2], ['(Обнять её)', 3]]);
          if (i === 0) {
            await g.k('Ни с кем! С… телевизором. Он плохо показывает, я его ругала.', 'worried');
            await g.k('Пожалуйста, не спрашивай сейчас. Я расскажу. Обещаю. Просто… не сегодня.', 'sad');
          } else if (i === 1) {
            await g.k('…Скажу. Обязательно. Спасибо, что не давишь.', 'smile');
          } else {
            g.kira.setPose('hug');
            await g.n('Ты молча обнимаешь её. Кира замирает, а потом утыкается тебе в плечо. Её плечи чуть дрожат.');
            await g.k('Ты тёплый. Ты знал, что ты тёплый? Здесь ничего не бывает тёплым по-настоящему.', 'cry');
            g.kira.setPose('idle');
          }
          await g.k('Иди спать, {p}. Утром всё будет хорошо. Утром всегда всё хорошо.', 'smile');
        } }],
      });
      g.world.setTV('off');
      await fadeOut(g, 1.5);
    },
  ],
};

function playerBed(g) {
  g.player.seated = true;
  g.player.teleport(1.05, 9.3, 0, -0.15);
  g.player.eyeTarget = 0.85;
  g.player.eye = 0.85;
}

export const D2_TOPICS = [];
