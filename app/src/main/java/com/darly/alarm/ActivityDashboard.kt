package com.darly.alarm

import android.app.*
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Updates values in place so sensor events never reset scrolling or dismiss dialogs. */
class ActivityDashboard(private val a:Activity,private val body:LinearLayout,private val toggle:()->Unit){
 private val format=NumberFormat.getIntegerInstance(Locale("ru"))
 private val total=Ui.text(a,"0",48f,Ui.WHITE,true)
 private val goalLabel=Ui.text(a,"",13f,Ui.muted)
 private val status=Ui.text(a,"",13f,Ui.muted)
 private val progress=ProgressRing(a)
 private val start=Ui.button(a,""){toggle()}
 private val summary=Ui.text(a,"",15f,Ui.WHITE)
 private val chart=Ui.row(a).apply{gravity=Gravity.BOTTOM}
 private val history=Ui.column(a)
 private val challenges=linkedMapOf<String,Triple<TextView,ProgressBar,TextView>>()
 init{
  val hero=Ui.card(a);val row=Ui.row(a);val numbers=Ui.column(a)
  numbers.addView(Ui.text(a,"ШАГОВ СЕГОДНЯ",11f,Ui.muted,true));numbers.addView(Ui.space(a,10));numbers.addView(total);numbers.addView(Ui.space(a,7));numbers.addView(goalLabel)
  row.addView(numbers,LinearLayout.LayoutParams(0,-2,1f));row.addView(progress,LinearLayout.LayoutParams(Ui.dp(a,100),Ui.dp(a,100)))
  hero.addView(row);hero.addView(Ui.space(a,18));hero.addView(status);hero.addView(Ui.space(a,16));hero.addView(start)
  hero.addView(Ui.space(a,10));hero.addView(Ui.outlineButton(a,"Изменить цель"){editGoal()});body.addView(hero);gap()
  body.addView(Ui.text(a,"За последние 7 дней",21f,Ui.WHITE,true));body.addView(Ui.space(a,12))
  val analytics=Ui.card(a);analytics.addView(chart,LinearLayout.LayoutParams(-1,Ui.dp(a,142)));analytics.addView(Ui.space(a,18));analytics.addView(summary);body.addView(analytics);gap()
  body.addView(Ui.text(a,"Челленджи",21f,Ui.WHITE,true));body.addView(Ui.space(a,8))
  body.addView(Ui.text(a,"Выбери свой темп. Прогресс считается по шагам, записанным DARLY.",13f,Ui.muted));body.addView(Ui.space(a,12))
  listOf("walk" to "Первая прогулка","week" to "Неделя движения","streak" to "Три активных дня").forEach{(kind,title)->
   val card=Ui.card(a);card.addView(Ui.text(a,title,18f,Ui.WHITE,true));card.addView(Ui.space(a,10))
   val text=Ui.text(a,"",13f,Ui.muted);val bar=ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal).apply{max=100;progressTintList=android.content.res.ColorStateList.valueOf(Ui.blue)}
   val button=Ui.outlineButton(a,""){
    val c=StepStore.challenges(a).firstOrNull{it.kind==kind}
    if(c==null||!LocalDate.now().isBefore(StepRules.challenge(c,StepStore.ledger(a).days,LocalDate.now()).end))StepStore.join(a,kind)
    else AlertDialog.Builder(a).setTitle("Отменить челлендж?").setMessage("Записанные шаги и история сохранятся.").setNegativeButton("Продолжить",null).setPositiveButton("Отменить челлендж"){_,_->StepStore.cancel(a,kind);refresh()}.show()
    refresh()
   }
   card.addView(text);card.addView(Ui.space(a,12));card.addView(bar,LinearLayout.LayoutParams(-1,Ui.dp(a,6)));card.addView(Ui.space(a,14));card.addView(button)
   challenges[kind]=Triple(text,bar,button);body.addView(card);body.addView(Ui.space(a,12))
  }
  gap();body.addView(Ui.text(a,"История",21f,Ui.WHITE,true));body.addView(Ui.space(a,12));body.addView(history);gap()
  body.addView(Ui.text(a,"Шаги считаются с момента включения учёта. До первого измерения датчика счётчик начинается с нуля; прошедшие шаги за сегодня не подставляются. При паузе шаги не записываются.",12f,Ui.muted));body.addView(Ui.space(a,12))
  body.addView(Ui.outlineButton(a,"Настройки уведомлений"){
   a.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,a.packageName))
  });body.addView(Ui.space(a,12))
  body.addView(Ui.outlineButton(a,"Разрешения и фоновые настройки"){a.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${a.packageName}")))});body.addView(Ui.space(a,12))
  body.addView(Ui.text(a,"Для фонового учёта на HyperOS разреши автозапуск DARLY и работу без ограничений батареи. Учёт не использует GPS и не отправляет историю шагов на сервер.",12f,Ui.muted))
  refresh()
 }
 private fun gap(){body.addView(Ui.space(a,24))}
 private fun editGoal(){
  val input=EditText(a).apply{inputType=InputType.TYPE_CLASS_NUMBER;setText(StepStore.goal(a).toString());selectAll()}
  val dialog=AlertDialog.Builder(a).setTitle("Цель шагов на день").setMessage("От 100 до 100 000 шагов. Цель прошлых дней не меняется.").setView(input).setNegativeButton("Отмена",null).setPositiveButton("Сохранить",null).create()
  dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
   val value=input.text.toString().toIntOrNull()
   if(value==null||value !in 100..100000){input.error="Введи число от 100 до 100 000";return@setOnClickListener}
   StepStore.setGoal(a,value);if(StepsService.active)a.startService(Intent(a,StepsService::class.java));refresh();dialog.dismiss()
  }};dialog.show()
 }
 fun refresh(){
  val today=LocalDate.now();val ledger=StepStore.ledger(a);val days=ledger.days;val steps=days.firstOrNull{it.date==today.toString()}?.steps?:0
  val goal=StepStore.goal(a);total.text=format.format(steps);goalLabel.text="из ${format.format(goal)} · цель на день";progress.set(steps,goal)
  start.text=if(StepsService.active)"Приостановить учёт" else if(StepStore.enabled(a))"Продолжить учёт" else "Начать отслеживание"
  start.isEnabled=StepsService.sensor(a)!=null
  status.text=when{
   StepsService.sensor(a)==null->"Датчик шагов не найден на этом устройстве."
   StepStore.error(a)!=null->StepStore.error(a)
   !StepsService.allowed(a)->"Для учёта нужен доступ к физической активности."
   StepsService.active->if(steps>=goal)"Цель выполнена. Отличная прогулка!" else "Учёт включён · ещё ${format.format((goal-steps).coerceAtLeast(0))} до цели"
   StepStore.enabled(a)->"Учёт остановлен системой. Нажми «Продолжить учёт»."
   else->"Учёт на паузе. История сохранена."
  }
  val week=days.filter{it.date>=today.minusDays(6).toString()&&it.date<=today.toString()}
  val month=days.filter{it.date>=today.minusDays(29).toString()&&it.date<=today.toString()}
  val best=days.maxByOrNull{it.steps}
  summary.text="7 дней · ${format.format(week.sumOf{it.steps})} шагов\n30 дней · ${format.format(month.sumOf{it.steps})} шагов\nСреднее за дни учёта · ${format.format(if(week.isEmpty())0 else week.sumOf{it.steps}/week.size)}\nСерия дней с целью · ${StepRules.streak(days,today)}\nРекорд · ${format.format(best?.steps?:0)}${best?.let{" · ${LocalDate.parse(it.date).format(DateTimeFormatter.ofPattern("d.MM"))}"}.orEmpty()}"
  chart.removeAllViews();val max=week.maxOfOrNull{it.steps}?.coerceAtLeast(1)?:1
  (6 downTo 0).forEach{offset->
   val date=today.minusDays(offset.toLong());val day=days.firstOrNull{it.date==date.toString()};val col=Ui.column(a).apply{gravity=Gravity.CENTER_HORIZONTAL}
   col.addView(Ui.text(a,day?.let{format.format(it.steps)}?:"—",9f,Ui.muted).apply{gravity=Gravity.CENTER})
   col.addView(Ui.space(a,8));val height=if(day==null||day.steps==0L)2 else (day.steps*76/max).toInt().coerceAtLeast(4)
   col.addView(Ui.space(a,76-height));col.addView(View(a).apply{background=Ui.shape(if(day==null)0x335c7692 else if(offset==0)Ui.blue else 0xff63889d.toInt(),Ui.dp(a,5).toFloat())},LinearLayout.LayoutParams(Ui.dp(a,18),Ui.dp(a,height)))
   col.addView(Ui.space(a,8));col.addView(Ui.text(a,date.format(DateTimeFormatter.ofPattern("EE",Locale("ru"))),10f,Ui.muted))
   col.contentDescription="${date}: ${day?.let{"${it.steps} шагов"}?:"нет данных"}"
   chart.addView(col,LinearLayout.LayoutParams(0,-2,1f))
  }
  val joined=StepStore.challenges(a)
  challenges.forEach{(kind,widgets)->
   val (text,bar,button)=widgets;val c=joined.firstOrNull{it.kind==kind}
   if(c==null){
    text.text=when(kind){"walk"->"3 000 шагов сегодня";"week"->"35 000 шагов за 7 календарных дней";else->"3 дня с целью ${format.format(goal)} за неделю"};bar.progress=0;button.text="Принять челлендж"
   }else{
    val p=StepRules.challenge(c,days,today);bar.progress=(p.value*100/p.target).coerceAtMost(100).toInt()
    text.text="${format.format(p.value)} / ${format.format(p.target)} ${if(kind=="streak")"дней по ${format.format(c.dailyGoal)} шагов" else "шагов"}\n"+when{p.completed->"Выполнен ✓";p.expired->"Завершён · попробуй ещё раз";else->"До ${p.end.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM",Locale("ru")))} включительно"}
    button.text=if(!today.isBefore(p.end))"Начать заново" else "Отменить челлендж"
   }
  }
  history.removeAllViews()
  if(days.isEmpty())history.addView(Ui.text(a,"История появится после включения учёта. До этого момента данных нет.",14f,Ui.muted))
  days.sortedByDescending{it.date}.take(14).forEach{day->
   val row=Ui.row(a);row.addView(Ui.text(a,LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale("ru"))),14f,Ui.muted),LinearLayout.LayoutParams(0,-2,1f))
   row.addView(Ui.text(a,"${format.format(day.steps)}${if(day.steps>=day.goal)" ✓" else ""}",15f,Ui.WHITE,true));history.addView(row);history.addView(Ui.space(a,12))
  }
 }
 private class ProgressRing(c:android.content.Context):View(c){
  private val paint=Paint(Paint.ANTI_ALIAS_FLAG);private val bounds=RectF();private var progress=0f
  fun set(steps:Long,goal:Int){progress=(steps.toFloat()/goal).coerceIn(0f,1f);contentDescription="Выполнено ${(progress*100).toInt()} процентов цели";invalidate()}
  override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){val pad=Ui.dp(context,9).toFloat();bounds.set(pad,pad,w-pad,h-pad)}
  override fun onDraw(canvas:Canvas){paint.style=Paint.Style.STROKE;paint.strokeWidth=Ui.dp(context,8).toFloat();paint.strokeCap=Paint.Cap.ROUND;paint.color=0x335c7692;canvas.drawArc(bounds,0f,360f,false,paint);paint.color=Ui.blue;canvas.drawArc(bounds,-90f,progress*360,false,paint);paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=Ui.dp(context,18).toFloat();canvas.drawText("${(progress*100).toInt()}%",width/2f,height/2f-(paint.ascent()+paint.descent())/2,paint)}
 }
}
