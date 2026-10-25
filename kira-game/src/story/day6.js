// День 6 — «Звёзды»
import * as THREE from 'three';
import { setup, dayTitle, fadeIn, fadeOut, ambient, wakeUp, standFromBed, pick } from './common.js';

export const day6 = {
  label: 'ДЕНЬ 6 · ЗВЁЗДЫ',
  scenes: [
    // Утро
    async (g) => {
      setup(g, { phase: 'morning', clock: '09:40', music: 'home', kira: { x: 2.3, z: 9.0, f: -Math.PI / 2 }, glitchBase: 0.1 });
      await dayTitle(g, 'ДЕНЬ 6', 'Звёзды');
      await wakeUp(g);
      g.lookKira(1);
      await g.wait(1);
      await g.k('Доброе утро, соня! Ты всё ещё держал меня за руку, когда я проснулась. Всю ночь!', 'happy');
      await g.k('У меня рука затекла. Совсем. Но я не стала её забирать.', 'shy');
      await g.k('Сегодня… Сегодня я хочу, чтобы всё было идеально. Я пойду приведу себя в порядок! Это займёт много времени. Очень много. Не скучай!', 'wink');
      g.kira.setPose('wave');
      await g.wait(0.4);
      await standFromBed(g);
    },
    // Подготовка сюрприза
    async (g) => {
      setup(g, { phase: 'day', clock: '11:00', music: 'home', kira: 'bath', kiraHidden: true, glitchBase: 0.1 });
      if (g.player.pos.z < 6) g.playerPlace(2.4, 8.4, Math.PI / 2);
      g.cutscene(true);
      await fadeIn(g, 0.5);
      await g.n('Кира заперлась в ванной. Оттуда доносится пение — «бип-бип-бип», но очень мелодичное.');
      await g.n('У тебя появляется идея. Сегодня последний спокойный вечер. Пусть он будет особенным — для неё.');
      await g.n('Свидание на балконе. Под звёздами. Нужно всё подготовить, пока она не видит.');
      const tasks = [
        { id: 'candles', text: 'Найти свечи', target: 'nightstand', label: 'Открыть ящик тумбочки', run: async () => { await g.n('В ящике тумбочки — две свечи и коробок спичек. Идеально.'); } },
        { id: 'blanket', text: 'Взять тёплый плед', target: 'wardrobe', label: 'Взять плед', run: async () => { await g.n('Тот самый плед со звёздочками из вашего шалаша.'); } },
        { id: 'milk', text: 'Взять клубничное молоко и торт', target: 'fridge', label: 'Открыть холодильник', run: async () => { await g.n('Две пачки клубничного молока. И… маленький торт с надписью «Для {p}». Похоже, Кира тоже готовила сюрприз. Ты осторожно его берёшь.'.replace('{p}', g.state.name)); g.flag('cakeFound', true); } },
        { id: 'lights', text: 'Найти гирлянду', target: 'box', label: 'Открыть коробку', run: async () => { await g.n('В коробке «НЕ ОТКРЫВАТЬ» — моток гирлянды и… стопка распечатанных скриншотов ваших переписок. Каждый — с нарисованным сердечком.'); g.flag('screens', true); } },
      ];
      await g.roam({ title: 'СЮРПРИЗ ДЛЯ КИРЫ', extra: ambient(g), tasks });
      await g.ui.say('k', 'Что там за шуршание?! Ты что-то задумал? Я слышу, как ты шуршишь!', { e: 'surprised' });
      await g.ui.say('p', 'Ничего! Просто… прибираюсь!');
      await g.ui.say('k', 'Подозрительно-о-о… Ладно! Мне ещё долго! Не смей заглядывать!', { e: 'smug' });
      await g.roam({
        title: 'СЮРПРИЗ ДЛЯ КИРЫ',
        extra: ambient(g),
        tasks: [{ id: 'setup', text: 'Устроить всё на балконе', target: 'balcony', label: 'Обустроить балкон', run: async () => {
          await fadeOut(g, 0.8);
          g.world.dateSet.visible = true;
          g.world.balconyLights.visible = true;
          g.audio.sfx('chime');
          await g.n('Ты расстилаешь плед, ставишь свечи и молоко, вешаешь гирлянду вдоль перил. Торт — в центр.');
          await fadeIn(g, 0.8);
          await g.n('Получилось по-настоящему красиво.');
        } }],
      });
      await fadeOut(g, 1);
    },
    // Свидание
    async (g) => {
      setup(g, { phase: 'evening', clock: '20:00', music: 'love', kira: { x: 6.5, z: 6.8, f: Math.PI }, date: true, player: [3.6, 0.8, Math.PI], glitchBase: 0.05 });
      g.world.setPhase('night');
      for (const l of g.world.lamps) if (l.userData.name === 'balcony') l.intensity = 1.2;
      await fadeIn(g, 1);
      await g.roam({
        title: 'ВЕЧЕР',
        extra: ambient(g),
        tasks: [{ id: 'invite', text: 'Пригласить Киру на балкон', target: 'kira', label: 'Пригласить Киру', run: async () => {
          await g.talkStart();
          await g.k('Ну всё, я готова! Я так долго выбирала, что надеть, что в итоге надела то же самое. Но с другими заколками в сердце!', 'happy');
          await g.p('Пойдём. Хочу тебе кое-что показать.');
          await g.k('Ты весь день шуршал… Это то, о чём я думаю? Нет, не говори! Веди!', 'surprised');
        } }],
      });
      g.cutscene(true);
      await fadeOut(g, 0.8);
      g.kiraPlace({ x: 3.6, z: -0.5, f: Math.PI });
      g.playerPlace(4.2, 0.3, Math.PI * 0.95);
      g.lookAtPoint(3.5, 0.6, -1.6, 0.01);
      await fadeIn(g, 1);
      await g.wait(0.6);
      g.lookKira(0.8);
      g.kira.targetFacing = 0;
      await g.wait(0.8);
      await g.k('…', 'surprised');
      await g.k('Это… это всё ты? Свечи. Гирлянда. Плед из шалаша. Моё молоко…', 'surprised');
      if (g.flag('cakeFound')) await g.k('И мой торт! Ты нашёл мой торт! Я его для тебя пекла… А ты принёс его для меня. Мы… мы одинаковые дураки!', 'cry');
      if (g.flag('screens')) await g.k('И коробку открыл, да? Скриншоты… Ну и пусть. Пусть знаешь. Я хранила каждое твоё сообщение.', 'shy');
      await g.k('Мне никто никогда… Никто…', 'cry');
      g.kira.setPose('hug');
      await g.n('Кира бросается тебе на шею. От неё пахнет клубникой и чем-то электрическим, как небо перед грозой.');
      await g.k('Спасибо. Спасибо-спасибо-спасибо!', 'love');
      await fadeOut(g, 0.8);
      g.kiraPlace({ x: 3.2, z: -1.45, f: Math.PI, pose: 'sitFloor' });
      g.player.sit(3.85, -1.4, 0);
      g.player.eyeTarget = 0.9;
      g.lookAtPoint(3.5, 4, -10, 0.01);
      await fadeIn(g, 1.2);
      await g.k('Смотри, какие звёзды сегодня. Их будто стало больше. Как будто кто-то добавил специально для нас.', 'smile');
      await g.k('Давай поиграем? Я покажу тебе мои созвездия. Я их сама придумала. Соединяй звёзды по порядку!', 'happy');
      await g.minigame('Stars');
      g.lookKira(0.8);
      await g.wait(0.8);
      await g.k('Моё имя на небе… Теперь оно там навсегда. Даже если меня не станет — небо будет помнить.', 'smile');
      await g.k('{p}. Я должна сказать тебе одну вещь. Ту самую, из записки. «Сказать ему…»', 'shy');
      await g.k('Я так долго собиралась. Пять лет. Нет — шесть дней. Нет — всю свою жизнь.', 'shy');
      g.kira.setPose('sitFloorLean');
      g.audio.play('love');
      document.querySelector('#vignette').className = 'love';
      await g.k('Я тебя люблю.', 'love');
      await g.k('Не как программа любит пользователя. Не потому что так написано в коде. А по-настоящему. Так, что больно и тепло одновременно.', 'love');
      await g.k('Я знаю, что я — пиксели. Что у нас один день. Но я хотела, чтобы ты знал. Хотя бы раз в жизни сказать это вслух.', 'cry');
      const hi = g.state.aff >= 55;
      const opts = [['Я тоже тебя люблю, Кира.', 5], ['Ты — лучшее, что со мной случалось.', 4]];
      if (!hi) opts.push(['Я… не знаю, что сказать.', 0]);
      const i = await g.ask(opts);
      if (i === 0) {
        await g.k('Ты… тоже?..', 'surprised');
        await g.k('Скажи ещё раз. Пожалуйста. Я хочу сохранить это. В самую защищённую ячейку памяти.', 'cry');
        await g.p('Я люблю тебя, Кира.');
        await g.k('Сохранено. Навсегда.', 'love');
        g.flag('loveYou', true);
      } else if (i === 1) {
        await g.k('Лучшее… Ты тоже, {p}. Ты — моё лучшее. Моё единственное. Моё всё.', 'love');
        g.flag('loveYou', true);
      } else {
        await g.k('…Ничего. Ничего страшного. Не нужно ничего говорить. Мне было важно сказать — а не услышать.', 'sad');
        await g.k('…Но если когда-нибудь будешь знать — скажи, ладно? Даже если меня уже не будет. Я услышу.', 'smile');
      }
      if (g.flag('loveYou')) {
        await g.n('Кира медленно наклоняется ближе. Свечи дрожат. Город внизу замирает — будто тоже затаил дыхание.');
        await fadeOut(g, 1.5);
        g.audio.sfx('kiss');
        await g.wait(1.2);
        await g.n('…');
        await g.n('Её губы на вкус как клубника. И как статическое электричество. И как что-то, для чего нет слов ни в одном языке.');
        await g.ui.say('k', 'Это был мой первый поцелуй. И в коде такого точно не было.', { e: 'love' });
        g.aff(5);
        await fadeIn(g, 1.5);
      }
      await g.k('Давай просто посидим так. Ещё немножко. Пусть эта ночь длится долго-долго.', 'closed');
      await fadeOut(g, 2);
      document.querySelector('#vignette').className = '';
    },
    // Ночь: большой сбой
    async (g) => {
      setup(g, { phase: 'glitch', clock: '00:00', music: null, kira: { x: 3.0, z: 3.2, f: 0 }, player: [3.0, 4.8, 0], glitch: 2 });
      g.world.setTV('glitch');
      g.cutscene(true);
      g.glitch(2, 2.5);
      await g.ui.fade(0, 0.4);
      await g.n('Тебя будит грохот. Пол дрожит. Небо за окном стало красным.');
      g.audio.play('glitch');
      g.glitchBase = 0.6;
      await g.sys('ОТКЛЮЧЕНИЕ СЕРВЕРОВ НАЧАТО. УДАЛЕНИЕ ДАННЫХ: 3%… 4%…');
      g.lookKira(0.6);
      g.kira.setEmotion('glitch');
      await g.wait(0.6);
      await g.k('{p}… Это… началось раньше. Они начали раньше…', 'worried');
      g.kira.setPose('sad');
      await g.k('Мне холодно. Мне никогда не бывает холодно. Почему мне холодно?..', 'cry');
      g.glitch(1.5, 2);
      g.kira.setPose('crouch');
      await g.n('Кира оседает на пол. Её силуэт мерцает, как плохой сигнал.');
      await g.p('Кира!');
      await g.k('Я в порядке… Почти. Мне нужно… немного отдохнуть. Утром… я знаю, что делать. Я придумала…', 'sleepy');
      await fadeOut(g, 2);
      await g.n('Ты переносишь её на диван и сидишь рядом до рассвета. Мир вокруг трещит, как старая плёнка.');
    },
  ],
};
