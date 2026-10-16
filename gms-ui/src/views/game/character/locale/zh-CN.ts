const fields = {
  level:'等级',job:'职业',exp:'当前等级经验',str:'力量 STR',dex:'敏捷 DEX',int:'智力 INT',luk:'运气 LUK',
  hp:'当前 HP',maxhp:'基础最大 HP',mp:'当前 MP',maxmp:'基础最大 MP',ap:'剩余能力点 AP',sp:'剩余技能点 SP（1–10 技能书）',hpMpUsed:'HP/MP 已使用能力点',
  meso:'金币',fame:'人气',gm:'GM 等级',gender:'性别',skincolor:'肤色 ID',face:'脸型 ID',hair:'发型 ID',map:'地图 ID',spawnpoint:'出生传送点 ID',
  equipslots:'装备栏格数',useslots:'消耗栏格数',setupslots:'设置栏格数',etcslots:'其他栏格数',buddyCapacity:'好友容量',
  mountlevel:'坐骑等级',mountexp:'坐骑经验',mounttiredness:'坐骑疲劳',reborns:'转生次数',pqpoints:'组队任务积分',dojoPoints:'武陵道场积分',ariantPoints:'阿里安特积分',
  vanquisherStage:'讨伐阶段',vanquisherKills:'讨伐击杀数',gachaexp:'百宝箱经验',nxCredit:'点券',maplePoint:'抵用券',nxPrepaid:'信用券',
};
const readonly = {id:'角色 ID',accountid:'账号 ID',world:'大区',name:'角色名称',guildid:'公会 ID',guildrank:'公会阶级',party:'队伍 ID',familyId:'家族 ID',partnerId:'配偶 ID',createdate:'创建时间',lastLogoutTime:'最后退出时间'};
export default {
  ...Object.fromEntries(Object.entries(fields).map(([key,value])=>[`characterAdmin.field.${key}`,value])),
  ...Object.fromEntries(Object.entries(readonly).map(([key,value])=>[`characterAdmin.readonly.${key}`,value])),
  'menu.game.character':'角色管理','characterAdmin.picker':'选择角色 / 搜索角色名或 ID','characterAdmin.more':'加载更多',
  'characterAdmin.online':'在线','characterAdmin.offline':'离线','characterAdmin.name':'角色','characterAdmin.account':'所属账号','characterAdmin.world':'大区','characterAdmin.status':'状态',
  'characterAdmin.locked':'角色或同账号正在游戏中，暂不可保存。退出该账号游戏后刷新。',
  'characterAdmin.accountShared':'点券、抵用券和信用券属于账号，同账号所有角色共享。',
  'characterAdmin.group.basic':'基础属性','characterAdmin.group.appearance':'外观','characterAdmin.group.location':'位置','characterAdmin.group.inventory':'背包与好友','characterAdmin.group.progress':'进度','characterAdmin.group.account':'账号资源',
  'characterAdmin.relations':'关联信息（只读）','characterAdmin.male':'男','characterAdmin.female':'女',
  'characterAdmin.preview':'预览修改','characterAdmin.reset':'还原输入','characterAdmin.history':'修改记录','characterAdmin.confirmTitle':'确认角色属性修改',
  'characterAdmin.changeWarning':'修改将直接保存到角色存档。更改职业不会自动重置技能；升级不会自动加点。保存前会记录修改前后数据。',
  'characterAdmin.attribute':'属性','characterAdmin.before':'修改前','characterAdmin.after':'修改后','characterAdmin.cancel':'取消','characterAdmin.confirm':'确认修改','characterAdmin.time':'时间','characterAdmin.actor':'操作人','characterAdmin.changes':'修改内容',
};
