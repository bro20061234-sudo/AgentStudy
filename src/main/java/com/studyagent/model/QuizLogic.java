package com.studyagent.model;

import com.studyagent.repository.ReviewRepository;
import java.util.*;

public final class QuizLogic {
    private QuizLogic(){}
    public static List<Integer> indices(ReviewRepository.State state,boolean needsReview){
        List<Integer> result=new ArrayList<>();for(int i=0;i<state.questions().size();i++)if(!needsReview||i>=state.answers().length||state.answers()[i]<0||state.answers()[i]!=ReviewRepository.correctIndex(state.questions().get(i)))result.add(i);return result;
    }
    public static ReviewRepository.State subset(ReviewRepository.State state,List<Integer> indices){
        List<QuizQuestion> q=new ArrayList<>();int[] answers=new int[indices.size()];for(int i=0;i<indices.size();i++){int n=indices.get(i);q.add(state.questions().get(n));answers[i]=n<state.answers().length?state.answers()[n]:-1;}return new ReviewRepository.State(q,answers,state.submitted(),state.completed());
    }
    public static List<QuizQuestion> choose(List<QuizQuestion> bank,int count,boolean all,Random random){
        if(bank.isEmpty())throw new IllegalArgumentException("Ngân hàng chưa có câu hỏi. Hãy tạo câu ở Ôn tập hoặc Tạo thêm câu hỏi.");
        if(!all&&(count<1||count>bank.size()))throw new IllegalArgumentException("Số câu phải từ 1 đến "+bank.size()+".");
        List<QuizQuestion> result=new ArrayList<>(bank);if(!all){Collections.shuffle(result,random);return List.copyOf(result.subList(0,count));}return List.copyOf(result);
    }
    public static int[] blank(int count){int[] a=new int[count];Arrays.fill(a,-1);return a;}
}
