#!/usr/bin/env python3
"""Build authentic Russian language asset bundles for Star Wars Commander.

Extracts base English strings from:
  - swcFiles/1576242239/strings/strings_en-us.json.android.assetbundle
  - swcFiles/1584443067/strings/strings-hn_en-us.json.android.assetbundle
Applies comprehensive Russian translations for UI, settings, buildings,
units, heroes, factions, currencies, combat, objectives, holonet, and crates.
Packs them into UnityFS asset bundles and updates manifest CRCs.
"""

import json
import os
import re
import struct
import sys
import zlib
import UnityPy

sys.path.append(os.path.join(os.path.dirname(__file__), "joe"))
import joe

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SWC_FILES = os.path.join(ROOT, "swcFiles")
MANIFEST_PATH = os.path.join(SWC_FILES, "manifests", "__manifest_zyngaswc_prod.02045.json")

# Complete Star Wars Commander glossary: English -> Russian
GLOSSARY = {
    # System & Settings
    "Settings": "Настройки",
    "SETTINGS": "НАСТРОЙКИ",
    "Language": "Язык",
    "Language: {0}": "Язык: {0}",
    "Choose Language": "Выберите язык",
    "Are you sure you want to change your language to {0}?": "Вы действительно хотите изменить язык на {0}?",
    "Sound FX": "Звуковые эффекты",
    "Music": "Музыка",
    "Notifications": "Уведомления",
    "Push Notifications": "Push-уведомления",
    "Day/Night Cycle": "Смена дня и ночи",
    "Visual Effects": "Визуальные эффекты",
    "Support": "Поддержка",
    "Help & Support": "Помощь и поддержка",
    "Privacy Policy": "Политика конфиденциальности",
    "Terms of Service": "Условия использования",
    "Credits": "Кредиты",
    "Created by": "Создатели",
    "Restore Progress": "Восстановить прогресс",
    "Discard Progress": "Сбросить прогресс",

    # UI Buttons & Common Words
    "OK": "ОК",
    "Ok": "ОК",
    "Cancel": "Отмена",
    "CANCEL": "ОТМЕНА",
    "Close": "Закрыть",
    "Back": "Назад",
    "Go Back": "Назад",
    "Next": "Далее",
    "Continue": "Продолжить",
    "Done": "Готово",
    "Save": "Сохранить",
    "Edit": "Изменить",
    "Help": "Помощь",
    "Info": "Инфо",
    "View": "Просмотр",
    "Select": "Выбрать",
    "Confirm": "Подтвердить",
    "Claim": "Получить",
    "Collect": "Собрать",
    "Train": "Обучить",
    "Upgrade": "Улучшить",
    "Move": "Переместить",
    "Rearm": "Перезарядить",
    "Speed Up": "Ускорить",
    "Finish Now": "Завершить сейчас",
    "Buy": "Купить",
    "Attack": "Атака",
    "Battle": "В бой",
    "Play": "Играть",
    "Play Now": "Играть сейчас",
    "Purchase Now": "Купить сейчас",
    "Deploy": "Высадить",
    "End Battle": "Завершить бой",
    "Find Match": "Поиск противника",
    "Find a Match": "Поиск противника",
    "Next Target": "Следующая цель",
    "Replay": "Повтор",
    "Share": "Поделиться",
    "Connect": "Подключить",
    "Free": "Бесплатно",
    "FREE": "БЕСПЛАТНО",
    "Full": "Заполнено",
    "FULL": "ЗАПОЛНЕНО",
    "Locked": "Заблокировано",
    "LOCKED": "ЗАБЛОКИРОВАНО",
    "Unlocked": "Разблокировано",
    "Loading...": "Загрузка...",
    "Loading": "Загрузка",
    "Reload": "Перезагрузить",
    "Retry": "Повторить",
    "Apply": "Применить",
    "Activate": "Активировать",
    "Deactivate": "Деактивировать",
    "Swap": "Поменять",
    "Request": "Запросить",
    "Donate": "Пожертвовать",
    "Join": "Вступить",
    "Leave": "Покинуть",
    "Create": "Создать",
    "Search": "Поиск",
    "Chat": "Чат",
    "Send": "Отправить",
    "Overview": "Обзор",
    "Forces": "Войска",
    "Worlds Unlocked": "Миров открыто",
    "Previous Chapter": "Предыдущая глава",
    "Next Chapter": "Следующая глава",
    "Next Battle": "Следующий бой",
    "Chapter Complete!": "Глава пройдена!",
    "Mission Complete": "Задание выполнено",
    "Mission Progress": "Прогресс задания",
    "Mission Reward": "Награда задания",
    "Campaign Progress": "Прогресс кампании",
    "Campaign Rewards": "Награды кампании",
    "Achievements": "Достижения",
    "Profile": "Профиль",
    "Promote": "Повысить",
    "Demote": "Понизить",
    "Members": "Участники",
    "Messages": "Сообщения",
    "Invites": "Приглашения",
    "Leaders": "Лидеры",
    "Players": "Игроки",
    "How To Play": "Как играть",
    "Coming Soon": "Скоро появится",
    "New!": "Новое!",
    "Available": "Доступно",
    "Cost:": "Стоимость:",
    "Damage:": "Урон:",
    "Health:": "Прочность:",
    "Range:": "Дальность:",
    "Housing:": "Вместимость:",
    "Defense": "Защита",
    "Attacker": "Атакующий",
    "Defender": "Защитник",
    "Attacks Won": "Побед в атаке",
    "Defenses Won": "Побед в обороне",
    "Donated": "Пожертвовано",
    "Received": "Получено",
    "Type here to chat": "Введите сообщение...",

    # Factions
    "Empire": "Империя",
    "Rebels": "Повстанцы",
    "Rebel": "Повстанец",
    "Galactic Empire": "Галактическая Империя",
    "Rebel Alliance": "Альянс повстанцев",
    "Smugglers": "Контрабандисты",
    "Smuggler": "Контрабандист",
    "Mercenaries": "Наемники",
    "Mercenary": "Наемник",
    "Faction : {0}": "Фракция: {0}",
    "JOIN THE EMPIRE": "ВСТУПИТЬ В ИМПЕРИЮ",
    "JOIN THE REBELS": "ВСТУПИТЬ К ПОВСТАНЦАМ",

    # Currencies & Resources
    "Alloy": "Сплавы",
    "Contraband": "Контрабанда",
    "Crystals": "Кристаллы",
    "Medals": "Медали",
    "Droids": "Дроиды",
    "Resources": "Ресурсы",
    "Treasure": "Сокровища",
    "Structures": "Постройки",
    "Turrets": "Турели",
    "Troops": "Войска",
    "Units": "Бойцы",
    "Vehicles": "Техника",
    "Heroes": "Герои",
    "Starships": "Звездолеты",
    "Equipment": "Снаряжение",
    "Perks": "Усиления",

    # Combat & Results
    "Victory!": "Победа!",
    "Victory": "Победа",
    "VICTORY": "ПОБЕДА",
    "Defeat!": "Поражение!",
    "Defeat": "Поражение",
    "DEFEAT": "ПОРАЖЕНИЕ",
    "Defeat:": "Поражение:",
    "Draw": "Ничья",
    "Damage Dealt": "Нанесенный урон",
    "Stars Earned": "Заработано звезд",
    "Medals Earned": "Заработано медалей",
    "Loot Available:": "Доступная добыча:",
    "Available Loot:": "Доступная добыча:",
    "Loot Earned": "Полученная добыча",
    "Battle Log": "Журнал битв",
    "Battle Over": "Бой окончен",
    "Remaining Time": "Оставшееся время",
    "Time Left": "Осталось времени",
    "Surrender": "Сдаться",
    "Destroy HQ": "Уничтожить штаб",
    "Save HQ": "Защитить штаб",
    "Active {0}d ago": "{0} дн. назад",
    "Active {0}h ago": "{0} ч. назад",
    "Active {0}m ago": "{0} мин. назад",

    # Planets
    "Tatooine": "Татуин",
    "Dandoran": "Дандоран",
    "Er'Kit": "Эркит",
    "Erkit": "Эркит",
    "Hoth": "Хот",
    "Yavin 4": "Явин 4",
    "Takodana": "Такодана",
    "Kashyyyk": "Кашиик",
    "Sullust": "Салласт",

    # Buildings (Titles & Names)
    "Headquarters": "Штаб",
    "Empire Headquarters": "Штаб Империи",
    "Rebel Headquarters": "Штаб Повстанцев",
    "Smuggler Headquarters": "Штаб Контрабандистов",
    "Barracks": "Казармы",
    "Barracks Upgrade Required": "Требуется улучшение казарм",
    "Factory": "Завод",
    "Factory Upgrade Required": "Требуется улучшение завода",
    "Starport": "Космопорт",
    "Unit Transport": "Транспорт войск",
    "Credit Market": "Рынок кредитов",
    "Credit Vault": "Хранилище кредитов",
    "Alloy Refinery": "Завод сплавов",
    "Alloy Depot": "Склад сплавов",
    "Contraband Safehouse": "Убежище контрабанды",
    "Safehouse": "Убежище",
    "Contraband Trade Port": "Торговый порт контрабанды",
    "Trade Port": "Торговый порт",
    "Cantina": "Кантина",
    "Armory": "Оружейная",
    "Research Lab": "Исследовательская лаборатория",
    "Shield Generator": "Генератор щита",
    "Wall": "Стена",
    "Walls": "Стены",
    "Turret": "Турель",
    "Rapid Fire Turret": "Скорострельная турель",
    "Burst Turret": "Орудийная турель",
    "Rocket Turret": "Ракетная турель",
    "Smuggler Rocket Turret": "Ракетная турель контрабандистов",
    "Mortar": "Миномет",
    "Mortar Turret": "Минометная турель",
    "Proton Mortar": "Протонный миномет",
    "Sonic Cannon Turret": "Звуковая пушка",
    "Superlaser Turret": "Суперлазерная турель",
    "Glop Turret": "Глоп-турель",
    "Autoturret": "Автотурель",
    "Droideka Platform": "Платформа дроидеки",
    "Droideka Sentinel Platform": "Платформа дроидеки-часового",
    "Droideka Oppressor Platform": "Платформа дроидеки-угнетателя",
    "Starship Command": "Командный центр звездолетов",
    "Starship Command Upgrade Required": "Требуется улучшение центра звездолетов",
    "Planetary Command": "Планетарный командный пункт",
    "Scout Tower": "Сторожевая башня",
    "Droid Hut": "Хижина дроидов",
    "Hero Command": "Командный центр героев",
    "Hero Command Upgrade Required": "Требуется улучшение центра героев",
    "Squad Center": "Штаб отряда",
    "Imperial Banner": "Имперское знамя",
    "Rebel Banner": "Знамя повстанцев",
    "Moisture Vaporator": "Влагоуловитель",
    "Resource Drill": "Буровая установка",
    "Sandstone Wall": "Песчаниковая стена",
    "Reinforced Sandstone Wall": "Укрепленная песчаниковая стена",
    "Hardened Sandstone Wall": "Закаленная песчаниковая стена",
    "Mud Wall": "Глиняная стена",
    "Mud Brick Wall": "Стена из глиняного кирпича",
    "Krayt Bone Wall": "Стена из костей крайт-дракона",
    "Krayt Cement Wall": "Стена из крайт-цемента",
    "Aggregate Wall": "Бетонная стена",
    "Strix Beacon": "Маяк «Стрикса»",

    # Traps & Obstacles
    "TIE Fighter Trap": "Ловушка: СИД-истребитель",
    "TIE Bomber Trap": "Ловушка: СИД-бомбардировщик",
    "TIE Striker Trap": "Ловушка: СИД-ударник",
    "X-wing Trap": "Ловушка: Крестокрыл",
    "Y-wing Trap": "Ловушка: Истребитель Y-Wing",
    "Fang Fighter Trap": "Ловушка: Истребитель «Фэнг»",
    "LAAT Trap": "Ловушка: LAAT",
    "IDT Trap": "Ловушка: IDT",
    "Mini-Turret Trap": "Ловушка: Мини-турель",
    "Small Debris": "Мелкие обломки",
    "Medium Debris": "Средние обломки",
    "Large Debris": "Крупные обломки",
    "Small Rock": "Маленький камень",
    "Medium Rock": "Средний камень",
    "Large Rock": "Большая скала",
    "Small Junk": "Мелкий мусор",
    "Medium Junk": "Средний мусор",
    "Large Junk": "Крупный мусор",
    "Small Rubble": "Небольшие завалы",
    "Medium Rubble": "Средние завалы",
    "Large Rubble": "Большие завалы",
    "Small Brush": "Мелкий кустарник",
    "Medium Brush": "Средний кустарник",
    "Large Brush": "Густой кустарник",
    "Small Icy Rock": "Маленькая ледяная глыба",
    "Medium Icy Rock": "Средняя ледяная глыба",
    "Large Icy Rock": "Большая ледяная глыба",
    "Womp Rat Nest": "Гнездо вомп-песчанок",
    "Womp Rat Warren": "Нора вомп-песчанок",
    "Crashed Ship Bow": "Нос разбитого корабля",
    "Crashed Ship Stern": "Корма разбитого корабля",

    # Troops & Entities
    "Stormtrooper": "Штурмовик",
    "Heavy Stormtrooper": "Тяжелый штурмовик",
    "Heavy Trooper": "Тяжелый штурмовик",
    "Heavy Soldier": "Тяжелый пехотинец",
    "Soldier": "Пехотинец",
    "Rebel Soldier": "Солдат повстанцев",
    "Wookiee Warrior": "Воин-вуки",
    "Jump Trooper": "Джамп-трупер",
    "Jetpack Trooper": "Джетпак-трупер",
    "Rebel Vanguard": "Авангард повстанцев",
    "Rebel Commando": "Диверсант повстанцев",
    "Rebel Sharpshooter": "Снайпер повстанцев",
    "Rebel Pathfinder": "Следопыт повстанцев",
    "Sniper Trooper": "Снайпер-штурмовик",
    "Scout Trooper": "Штурмовик-разведчик",
    "Shock Trooper": "Ударный штурмовик",
    "Sandtrooper": "Песчаный штурмовик",
    "Heavy Sandtrooper": "Тяжелый песчаный штурмовик",
    "Snowtrooper": "Снежный штурмовик",
    "Heavy Snowtrooper": "Тяжелый снежный штурмовик",
    "Shoretrooper": "Береговой штурмовик",
    "Heavy Shoretrooper": "Тяжелый береговой штурмовик",
    "Cold-weather Soldier": "Пехотинец для холодных миров",
    "Cold-weather Heavy Soldier": "Тяжелый пехотинец для холодных миров",
    "Desert Soldier": "Пехотинец пустыни",
    "Heavy Desert Soldier": "Тяжелый пехотинец пустыни",
    "Death Trooper": "Штурмовик смерти",
    "Heavy Undead Trooper": "Тяжелый штурмовик-нежить",
    "Undead Trooper": "Штурмовик-нежить",
    "Scout Undead Trooper": "Разведчик-нежить",
    "Royal Guard": "Императорский гвардеец",
    "501st Legion Captain": "Капитан 501-го легиона",
    "Phase II Dark Trooper": "Темный штурмовик фазы II",
    "Alliance Starfighter Pilot": "Пилот Альянса",
    "Imperial Starfighter Pilot": "Имперский пилот",
    "Imperial Astromedic": "Имперский астромедик",
    "Medic Droid": "Дроид-медик",
    "Medic": "Медик",
    "Repair Droid": "Ремонтный дроид",
    "Security Droid": "Дроид охраны",
    "Worker Droid": "Дроид-рабочий",
    "Probe Droid": "Дроид-разведчик",
    "Demolition Droid": "Дроид-подрывник",
    "LIN Demolitionmech": "Подрывной дроид LIN",
    "IG-86 Assassin Droid": "Дроид-убийца IG-86",
    "Droideka Sentinel": "Дроидека-часовой",
    "Droideka Sentinal": "Дроидека-часовой",
    "Droideka Oppressor": "Дроидека-угнетатель",
    "Rebel Hailfire Droid": "Дроид «Огненный град» повстанцев",
    "Seized Hailfire Droid": "Захваченный дроид «Огненный град»",

    # Vehicles & Walkers
    "Speeder Bike": "Спидербайк",
    "2-M Hover Tank": "Парящий танк 2-M",
    "AAT-1 Hover Tank": "Парящий танк AAT-1",
    "Seized AAT-1 Hover Tank": "Захваченный парящий танк AAT-1",
    "TX-225": "Танк TX-225",
    "Stolen TX-225": "Угнанный танк TX-225",
    "T2-B Repulsor Tank": "Репульсорный танк T2-B",
    "Enhanced T2-B Repulsor Tank": "Улучшенный репульсорный танк T2-B",
    "Juggernaut": "Джаггернаут",
    "Seized Juggernaut": "Захваченный джаггернаут",
    "A-A5 Speeder Truck": "Спидер-грузовик A-A5",
    "Anti-Vehicle Skiff": "Противотранспортный скиф",
    "Mobile Heavy Cannon": "Мобильное тяжелое орудие",
    "Stolen Mobile Heavy Cannon": "Угнанное тяжелое орудие",
    "Imperial Troop Transport": "Имперский десантный транспорт",
    "Salvaged Multi-Troop Transport": "Восстановленный десантный транспорт",
    "AT-ST": "АТ-СТ",
    "AT-ST Walker": "Шагоход АТ-СТ",
    "Enhanced AT-ST Walker": "Улучшенный шагоход АТ-СТ",
    "Veteran AT-ST": "Ветеранский АТ-СТ",
    "Stolen AT-ST Walker": "Угнанный шагоход АТ-СТ",
    "AT-AT": "АТ-АТ",
    "Elite AT-AT": "Элитный АТ-АТ",
    "Stolen AT-AT Walker": "Угнанный шагоход АТ-АТ",
    "AT-DP": "АТ-ДП",
    "Stolen AT-DP Walker": "Угнанный шагоход АТ-ДП",
    "AT-DT Walker": "Шагоход АТ-ДТ",
    "Stolen AT-DT Walker": "Угнанный шагоход АТ-ДТ",
    "AT-MP": "АТ-МП",
    "AT-MP Mark III": "АТ-МП Модель III",
    "Enhanced AT-MP Walker": "Улучшенный шагоход АТ-МП",
    "AT-AP Walker": "Шагоход АТ-АП",
    "AT-AP Veteran": "Ветеранский АТ-АП",
    "Enhanced AT-AP Walker": "Улучшенный шагоход АТ-АП",
    "AT-RT Walker": "Шагоход АТ-РТ",
    "AT-TE": "АТ-ТЕ",
    "Elite AT-TE Walker": "Элитный шагоход АТ-ТЕ",
    "Seized AT-TE Walker": "Захваченный шагоход АТ-ТЕ",
    "V-4X-D Ski Speeder": "Лыжный спидер V-4X-D",

    # Heroes & Characters
    "Darth Vader": "Дарт Вейдер",
    "Lord Vader": "Лорд Вейдер",
    "Luke Skywalker": "Люк Скайуокер",
    "Han Solo": "Хан Соло",
    "Princess Leia": "Принцесса Лея",
    "Leia Organa": "Лея Органа",
    "Chewbacca": "Чубакка",
    "General Veers": "Генерал Вирс",
    "Admiral Ozzel": "Адмирал Оззель",
    "Admiral Ackbar": "Адмирал Акбар",
    "Mon Mothma": "Мон Мотма",
    "Lando Calrissian": "Лэндо Калриссиан",
    "Maz Kanata": "Маз Каната",
    "Boba Fett": "Боба Фетт",
    "Bossk": "Босск",
    "Hondo Ohnaka": "Хондо Онака",
    "Johhar Kessen": "Джохар Кессен",
    "Saponza": "Сапонза",
    "R2-D2": "R2-D2",
    "Bib Fortuna": "Биб Фортуна",

    # Creatures & Mercenaries
    "Dewback": "Дьюбек",
    "Dewback Trooper": "Наездник дьюбека",
    "Bantha Rider": "Наездник банты",
    "Fathier Rider": "Наездник фатира",
    "Rancor": "Ранкор",
    "Hunter Rancor": "Охотничий ранкор",
    "Shadow Rancor": "Теневой ранкор",
    "Rage Rancor": "Яростный ранкор",
    "Krayt Dragon": "Крайт-дракон",
    "Womp Rat": "Вомп-песчанка",
    "Heavy Womp Rat": "Крупная вомп-песчанка",
    "Alpha Womp Rat": "Альфа вомп-песчанка",
    "Gamorrean Warrior": "Гаморреанский воин",
    "Tusken Raider": "Тускенский рейдер",
    "Tusken Warrior": "Тускенский воин",
    "Tusken Chieftain": "Тускенский вождь",
    "Tusken Bantha Rider": "Тускенский наездник банты",
    "Jawa": "Джава",
    "Jawa Saboteur": "Джава-диверсант",
    "Dowutin Hunter": "Довутинский охотник",
    "Drabatan Saboteur": "Драбатанский диверсант",
    "Iakaru Warrior": "Воин-иакару",
    "Gigoran Fighter": "Гигоранский боец",
    "Kubaz Invader": "Кубазский захватчик",
    "Orbak Marauder": "Мародер на орбаке",
    "Elite Orbak Marauder": "Элитный мародер на орбаке",
    "Ongidae Oppressor": "Онгидайский угнетатель",

    # Equipment & Holonet
    "Black Market Objectives": "Цели черного рынка",
    "Black Market Objective Rush": "Штурм целей черного рынка",
    "Black Market Objectives - Expired": "Цели черного рынка (истекло)",
    "Squad Application Accepted": "Заявка в отряд принята",
    "Armory Required": "Требуется оружейная",
    "Affected Unit:": "Действует на боевую единицу:",
    "Equipment Capacity:": "Вместимость снаряжения:",
    "All": "Все",
    "Cosmetic": "Декоративное",
    "Supply Crates": "Контейнеры с припасами",
    "Event Crates": "Контейнеры событий",
    "Event Crate": "Контейнер события",
    "Bonus Event Crate": "Бонусный контейнер события",
    "Special Promotion": "Специальная акция",
    "Bronzium Crate": "Бронзиевый контейнер",
    "Durasteel Crate": "Дюрасталевый контейнер",
    "Obsidian Conflict Crate": "Обсидиановый контейнер конфликта",
    "Carbonite Conflict Crate": "Карбонитовый контейнер конфликта",
    "Bronzium Conflict Crate": "Бронзиевый контейнер конфликта",
    "Ultra Chrome Conflict Crate": "Ультрахромовый контейнер конфликта",
    "Durasteel Conflict Crate": "Дюрасталевый контейнер конфликта",
    "Weapons Research Crate": "Контейнер исследований оружия",
    "Arctic Ops Crate": "Контейнер арктической операции",
    "Desert Ops Crate": "Контейнер операции в пустыне",
    "Forest Ops Crate": "Контейнер операции в лесу",
    "Naval Assault Crate": "Контейнер морского десанта",
    "Undead Offensive Crate": "Контейнер наступления нежити",
    "Daily Anniversary Crate": "Ежедневный юбилейный контейнер",
    "May The 4th Crate": "Контейнер 4 мая",
    "Troop Equipment Unlock Crate": "Контейнер снаряжения войск",
    "Troops and Vehicles Unlock Crate": "Контейнер войск и техники",
    "Building Equipment Unlock Crate": "Контейнер снаряжения построек",
    "Cantina Mercenary Unlock Crate": "Контейнер наемников кантины",
    "Guard Reserve Unlock Crate": "Контейнер резерва охраны",
    "Starships and Starship Equipment Unlock Crate": "Контейнер звездолетов и снаряжения",
    "Heroes and Creatures Unlock Crate": "Контейнер героев и существ",
    "Heroes and Vehicles Equipment Unlock Crate": "Контейнер снаряжения героев и техники",

    # Campaigns & Raids
    "Rebel Outpost": "Аванпост повстанцев",
    "Rebel Base": "База повстанцев",
    "Empire Base": "База Империи",
    "Destroy the Rebel base!": "Уничтожьте базу повстанцев!",
    "Destroy the Imperial garrison!": "Уничтожьте имперский гарнизон!",
    "Destroy the Smuggler Outpost!": "Уничтожьте аванпост контрабандистов!",
    "Destroy the infected base!": "Уничтожьте зараженную базу!",
    "Destroy the captured base!": "Уничтожьте захваченную базу!",
    "Heroic Defense": "Героическая оборона",
    "Secret Alliances": "Тайные союзы",
    "Echoes of the Force": "Эхо Силы",
    "The Blood Dawn": "Кровавый рассвет",
    "Campaign III: The Sickness": "Кампания III: Болезнь",
    "Conflict on Tatooine": "Конфликт на Татуине",
    "Conflict on Dandoran": "Конфликт на Дандоране",
    "Conflict on Er'Kit": "Конфликт на Эрките",
    "Conflict on Hoth": "Конфликт на Хоте",
    "Conflict on Yavin 4": "Конфликт на Явине 4",
    "Conflict on Takodana": "Конфликт на Такодане",
    "Conflict at Boonta Eve": "Конфликт в Бунта Ив",
    "Conflict at Smuggler's Basin": "Конфликт во Впадине контрабандистов",
    "Conflict at Anchorhead Pass": "Конфликт в перевале Анкорхед",
    "Conflict at Massassi Valley": "Конфликт в долине Массасси",
    "Conflict at Luun Heights": "Конфликт на высотах Луун",
    "Conflict at Jundland Chasm": "Конфликт в расселине Юндланд",
    "Conflict at Skygazer Hill": "Конфликт на холме Звездочетов",
    "Conflict at Gundark Pass": "Конфликт в перевале Гундарка",
    "Conflict at Mynock Grottos": "Конфликт в гротах Майнока",
}

# Load extended Russian dictionary if present
EXT_DICT_PATH = os.path.join(os.path.dirname(__file__), "russian_dictionary.json")
if os.path.exists(EXT_DICT_PATH):
    with open(EXT_DICT_PATH, "r", encoding="utf-8") as _f:
        GLOSSARY.update(json.load(_f))
for _chunk_i in range(1, 9):
    _chunk_p = f"/tmp/swc_trans/out/c{_chunk_i}.json"
    if os.path.exists(_chunk_p):
        try:
            with open(_chunk_p, "r", encoding="utf-8") as _f:
                GLOSSARY.update(json.load(_f))
        except Exception:
            pass

# Regex pattern transforms
PATTERNS = [
    (r"^Level\s+(\d+)$", r"Уровень \1"),
    (r"^Requires\s+HQ\s+Level\s+(\d+)$", r"Требуется штаб уровня \1"),
    (r"^Requires\s+HQ\s+(\d+)$", r"Требуется штаб \1"),
    (r"^Requires\s+Level\s+(\d+)$", r"Требуется уровень \1"),
    (r"^Requires\s+(.+)\s+level\s+(\d+)$", r"Требуется \1 уровня \2"),
    (r"^Capacity:\s*(\{0\}|\d+)$", r"Вместимость: \1"),
    (r"^Health:\s*(\{0\}|\d+)$", r"Прочность: \1"),
    (r"^Damage:\s*(\{0\}|\d+)$", r"Урон: \1"),
    (r"^Range:\s*(\{0\}|\d+)$", r"Дальность: \1"),
    (r"^DPS:\s*(\{0\}|\d+)$", r"Урон в сек.: \1"),
    (r"^Training\s+Time:\s*(\{0\}|\d+.*)$", r"Время обучения: \1"),
    (r"^Upgrade\s+Time:\s*(\{0\}|\d+.*)$", r"Время улучшения: \1"),
    (r"^Cost:\s*(\{0\}|\d+.*)$", r"Стоимость: \1"),
    (r"^Upgrade\s+Cost:\s*(\{0\}|\d+.*)$", r"Стоимость улучшения: \1"),
    (r"^Upgrading\s+to\s+Level\s+(\d+)$", r"Улучшение до уровня \1"),
    (r"^Upgrade\s+to\s+Level\s+(\d+)$", r"Улучшить до уровня \1"),
    (r"^Call Sign\s*:\s*(\{0\})$", r"Позывной: \1"),
    (r"^Medals\s*:\s*(\{0\})$", r"Медали: \1"),
    (r"^Chapter\s+(\d+)$", r"Глава \1"),
    (r"^Mission\s+(\d+)$", r"Задание \1"),
    (r"^Tier\s+(\d+)$", r"Ранг \1"),
    (r"^Round\s+(\d+)$", r"Раунд \1"),
    (r"^Day\s+(\d+)$", r"День \1"),
    (r"^Collect\s+(\{0\}|\d+)\s+data\s+fragments\s+to\s+unlock$", r"Соберите \1 фрагментов данных для открытия"),
    (r"^Collect\s+(\{0\}|\d+)\s+data\s+fragments\s+to\s+upgrade$", r"Соберите \1 фрагментов данных для улучшения"),
    (r"^Equipment\s+for\s+(.+)$", r"Снаряжение для \1"),
    (r"^Free\s+Crate\s+For\s+(.+)$", r"Бесплатный контейнер за: \1"),
    (r"^Expires\s+in:\s*(\{0\})$", r"Истекает через: \1"),
    (r"^Squad\s+Level\s+(\{0\}|\d+)\s+Required$", r"Требуется отряд уровня \1"),
    (r"^Active\s+(\{0\}|\d+)d\s+ago$", r"\1 дн. назад"),
    (r"^Active\s+(\{0\}|\d+)h\s+ago$", r"\1 ч. назад"),
    (r"^Active\s+(\{0\}|\d+)m\s+ago$", r"\1 мин. назад"),
    (r"^(\d+)d$", r"\1 дн."),
    (r"^(\d+)h$", r"\1 ч."),
    (r"^(\d+)m$", r"\1 мин."),
    (r"^(\d+)s$", r"\1 с."),
]

def translate_text(uid, text):
    """Translate a single string into Russian, keeping format intact."""
    if not text or not text.strip():
        return text

    # Direct match in glossary
    if text in GLOSSARY:
        return GLOSSARY[text]

    # Pattern matches
    for pattern, replacement in PATTERNS:
        if re.match(pattern, text, re.IGNORECASE):
            return re.sub(pattern, replacement, text, flags=re.IGNORECASE)

    # UID-based rules
    if uid == "ALL_LANGUAGES":
        return "English (US)|Español|Français|Deutsch|Italiano|Português|简体中文|繁體中文|日本語|한국어|Русский"
    if uid == "SETTINGS_CHOOSELANGUAGE":
        return "Выберите язык"
    if uid == "SETTINGS_CONFIRMLANGUAGE":
        return "Вы действительно хотите изменить язык на {0}?"
    if uid == "SETTINGS_LANGUAGE":
        return "Язык: {0}"
    if uid == "LANG_BATTLE_ON_PLANET":
        return "{0} на {1}"
    if uid.startswith("s_timespan_"):
        if uid == "s_timespan_days":
            return "{0}д {1}ч {2}м {3}с"
        if uid == "s_timespan_hours":
            return "{0}ч {1}м {2}с"
        if uid == "s_timespan_minutes":
            return "{0}м {1}с"
        if uid == "s_timespan_seconds":
            return "{0}с"

    return text


def build_joe_data(source_bundle_path, target_asset_name):
    """Read a bundle, translate its JOE sheet, and return serialized JOE bytes."""
    env = UnityPy.load(source_bundle_path)
    for obj in env.objects:
        if obj.type.name == "TextAsset":
            raw = obj.get_raw_data()
            name_len = struct.unpack_from("<I", raw, 0)[0]
            offset = 4 + name_len
            if offset % 4 != 0:
                offset += 4 - (offset % 4)
            script_len = struct.unpack_from("<I", raw, offset)[0]
            offset += 4
            script_bytes = raw[offset : offset + script_len]
            parsed = joe.parse(script_bytes)
            tail = script_bytes[parsed["end"] :]

            sh = parsed["sheets"][0]
            ncols = len(sh["cols"])
            nrows = len(sh["cells"]) // ncols

            translated_count = 0
            string_map = {s: idx for idx, s in enumerate(parsed["S"])}

            def get_str_idx(s_bytes):
                if s_bytes in string_map:
                    return string_map[s_bytes]
                new_idx = len(parsed["S"])
                parsed["S"].append(s_bytes)
                string_map[s_bytes] = new_idx
                return new_idx

            new_cells = list(sh["cells"])
            for r in range(nrows):
                u_idx = sh["cells"][r * ncols]
                t_idx = sh["cells"][r * ncols + 1]

                u = parsed["S"][u_idx - 1].decode("utf-8", "ignore") if u_idx > 0 else ""
                t = parsed["S"][t_idx - 1].decode("utf-8", "ignore") if t_idx > 0 else ""

                ru_text = translate_text(u, t)
                if ru_text != t:
                    translated_count += 1
                    new_idx = get_str_idx(ru_text.encode("utf-8"))
                    new_cells[r * ncols + 1] = new_idx + 1

            sh["cells"] = new_cells
            print(f"[{target_asset_name}] Translated {translated_count} / {nrows} strings")
            return joe.write(parsed, tail)

    raise ValueError(f"No TextAsset in {source_bundle_path}")


def create_assetbundle(source_bundle, target_bundle_path, target_asset_name, joe_bytes):
    """Wrap new JOE bytes into a UnityFS assetbundle using source as template."""
    env = UnityPy.load(source_bundle)
    target_bundle_name = os.path.basename(target_bundle_path)

    for obj in env.objects:
        if obj.type.name == "TextAsset":
            data = obj.read()
            data.m_Name = target_asset_name
            data.m_Script = joe_bytes.decode("utf-8", "surrogateescape")
            data.save()
        elif obj.type.name == "AssetBundle":
            data = obj.read()
            data.m_Name = target_bundle_name
            data.m_AssetBundleName = target_bundle_name
            data.m_Container = [(k.replace("en-us", "ru-ru"), v) for k, v in data.m_Container]
            data.save()

    os.makedirs(os.path.dirname(target_bundle_path), exist_ok=True)
    with open(target_bundle_path, "wb") as f:
        f.write(env.file.save(packer="original"))
    print(f"Wrote {target_bundle_path} ({os.path.getsize(target_bundle_path)} bytes)")


def update_manifest(crc_main_bundle, crc_main_joe, crc_hn_bundle, crc_hn_joe):
    """Update all manifests in swcFiles/manifests/ with CRCs and v=1606000000."""
    NEW_V = 1606000000
    manifest_dir = os.path.join(SWC_FILES, "manifests")
    for fname in os.listdir(manifest_dir):
        if fname.startswith("__manifest_zyngaswc_prod.") and fname.endswith(".json"):
            mpath = os.path.join(manifest_dir, fname)
            with open(mpath, "r", encoding="utf-8") as f:
                manifest = json.load(f)

            # Main strings aliases
            for key in [
                "strings/strings_ru-ru.json.android.assetbundle",
                "strings/strings_ru-RU.json.android.assetbundle",
            ]:
                manifest["paths"][key] = {"crc": crc_main_bundle, "v": NEW_V}

            for key in [
                "strings/strings_ru-RU.json.joe",
                "strings/strings_ru-ru.json.joe",
                "strings/strings_ru-RU.json",
                "strings/strings_ru-ru.json",
            ]:
                manifest["paths"][key] = {"crc": crc_main_joe, "v": NEW_V}

            # Holonet strings aliases
            for key in [
                "strings/strings-hn_ru-ru.json.android.assetbundle",
                "strings/strings-hn_ru-RU.json.android.assetbundle",
            ]:
                manifest["paths"][key] = {"crc": crc_hn_bundle, "v": NEW_V}

            for key in [
                "strings/strings-hn_ru-RU.json.joe",
                "strings/strings-hn_ru-ru.json.joe",
                "strings/strings-hn_ru-RU.json",
                "strings/strings-hn_ru-ru.json",
            ]:
                manifest["paths"][key] = {"crc": crc_hn_joe, "v": NEW_V}

            if "dad436c8" in fname:
                manifest["version"] = "02049"
            elif "02045" in fname:
                manifest["version"] = "02045"

            with open(mpath, "w", encoding="utf-8") as f:
                json.dump(manifest, f, separators=(",", ":"))

            print(f"Updated {fname} (v={NEW_V})")


def main():
    en_main_bundle = os.path.join(SWC_FILES, "1576242239", "strings", "strings_en-us.json.android.assetbundle")
    en_hn_bundle = os.path.join(SWC_FILES, "1584443067", "strings", "strings-hn_en-us.json.android.assetbundle")

    print("Building strings_ru-ru.json.android.assetbundle...")
    joe_main = build_joe_data(en_main_bundle, "strings_ru-RU.json.joe")
    crc_main_joe = f"{zlib.crc32(joe_main) & 0xFFFFFFFF:08X}"

    # Write main bundle to version folders
    p_main_1 = os.path.join(SWC_FILES, "1576242239", "strings", "strings_ru-ru.json.android.assetbundle")
    p_main_2 = os.path.join(SWC_FILES, "1604000000", "strings", "strings_ru-ru.json.android.assetbundle")
    p_main_3 = os.path.join(SWC_FILES, "1605000000", "strings", "strings_ru-ru.json.android.assetbundle")
    p_main_4 = os.path.join(SWC_FILES, "1606000000", "strings", "strings_ru-ru.json.android.assetbundle")
    for pm in [p_main_1, p_main_2, p_main_3, p_main_4]:
        create_assetbundle(en_main_bundle, pm, "strings_ru-RU.json.joe", joe_main)
    crc_main_bundle = f"{zlib.crc32(open(p_main_1, 'rb').read()) & 0xFFFFFFFF:08X}"

    # Raw joe and json files (do NOT overwrite case-insensitive bundle path on macOS)
    for base_p in [p_main_1, p_main_2, p_main_3, p_main_4]:
        folder = os.path.dirname(base_p)
        bundle_upper = os.path.join(folder, "strings_ru-RU.json.android.assetbundle")
        if not (os.path.exists(bundle_upper) and os.path.samefile(base_p, bundle_upper)):
            data_bytes = open(base_p, "rb").read()
            with open(bundle_upper, "wb") as f:
                f.write(data_bytes)
        for name in ["strings_ru-ru.json.joe", "strings_ru-ru.json"]:
            with open(os.path.join(folder, name), "wb") as f:
                f.write(joe_main)

    print("Building strings-hn_ru-ru.json.android.assetbundle...")
    joe_hn = build_joe_data(en_hn_bundle, "strings-hn_ru-RU.json.joe")
    crc_hn_joe = f"{zlib.crc32(joe_hn) & 0xFFFFFFFF:08X}"

    p_hn_1 = os.path.join(SWC_FILES, "1584443067", "strings", "strings-hn_ru-ru.json.android.assetbundle")
    p_hn_2 = os.path.join(SWC_FILES, "1604000000", "strings", "strings-hn_ru-ru.json.android.assetbundle")
    p_hn_3 = os.path.join(SWC_FILES, "1605000000", "strings", "strings-hn_ru-ru.json.android.assetbundle")
    p_hn_4 = os.path.join(SWC_FILES, "1606000000", "strings", "strings-hn_ru-ru.json.android.assetbundle")
    for ph in [p_hn_1, p_hn_2, p_hn_3, p_hn_4]:
        create_assetbundle(en_hn_bundle, ph, "strings-hn_ru-RU.json.joe", joe_hn)
    crc_hn_bundle = f"{zlib.crc32(open(p_hn_1, 'rb').read()) & 0xFFFFFFFF:08X}"

    for base_p in [p_hn_1, p_hn_2, p_hn_3, p_hn_4]:
        folder = os.path.dirname(base_p)
        bundle_upper = os.path.join(folder, "strings-hn_ru-RU.json.android.assetbundle")
        if not (os.path.exists(bundle_upper) and os.path.samefile(base_p, bundle_upper)):
            data_bytes = open(base_p, "rb").read()
            with open(bundle_upper, "wb") as f:
                f.write(data_bytes)
        for name in ["strings-hn_ru-ru.json.joe", "strings-hn_ru-ru.json"]:
            with open(os.path.join(folder, name), "wb") as f:
                f.write(joe_hn)

    print("Updating manifests...")
    update_manifest(crc_main_bundle, crc_main_joe, crc_hn_bundle, crc_hn_joe)
    print("Done!")

if __name__ == "__main__":
    main()
